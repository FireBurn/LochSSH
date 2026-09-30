package uk.co.fireburn.lochssh.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.io.File
import java.io.IOException
import java.net.Socket
import java.net.ServerSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class SharedSshConnectionTest {
    @Test
    fun keepsTunnelsUntilTheLastShellReleasesTheConnection() = withSession { session ->
        val localPort = session.setPortForwardingL(0, "127.0.0.1", 22)
        val proxy = Socks5Forwarder(session, 0)
        proxy.start()
        val proxyPort = proxy.listenPort
        val connection = SharedSshConnection(session, emptyList(), listOf(proxy))
        try {
            assertTrue(connection.acquire())

            connection.release()

            assertTrue(session.isConnected)
            Socket("127.0.0.1", localPort).use { socket ->
                socket.soTimeout = 3_000
                assertEquals("SSH-", socket.getInputStream().readNBytes(4).toString(Charsets.UTF_8))
            }
            Socket("127.0.0.1", proxyPort).use { assertTrue(it.isConnected) }

            Socket().use { localProbe ->
                Socket().use { proxyProbe ->
                    // Reserve source ports while listeners still own their ports.
                    localProbe.bind(InetSocketAddress("127.0.0.1", 0))
                    proxyProbe.bind(InetSocketAddress("127.0.0.1", 0))
                    connection.release()

                    assertFalse(session.isConnected)
                    assertFalse(connection.acquire())
                    assertThrows(IOException::class.java) {
                        localProbe.connect(InetSocketAddress("127.0.0.1", localPort), 3_000)
                    }
                    assertThrows(IOException::class.java) {
                        proxyProbe.connect(InetSocketAddress("127.0.0.1", proxyPort), 3_000)
                    }
                }
            }
        } finally {
            proxy.stop()
        }
    }

    @Test
    fun cancellingOneChannelKeepsTheSharedConnectionUsable() = withSession { session ->
        val pending = session.openChannel("exec") as ChannelExec
        pending.setCommand("sleep 30")
        val channels = ConnectingChannels()
        channels.add(pending)
        pending.connect(3_000)

        channels.close()

        assertFalse(pending.isConnected)
        assertTrue(session.isConnected)
        val remaining = session.openChannel("exec") as ChannelExec
        try {
            remaining.setCommand("printf retained")
            val input = remaining.inputStream
            remaining.connect(3_000)
            assertEquals("retained", input.readNBytes(8).toString(Charsets.UTF_8))
        } finally {
            remaining.disconnect()
        }
    }

    @Test
    fun refusesToShareAConnectionThatHasLostItsServer() = withSession { session ->
        val connection = SharedSshConnection(session, emptyList(), emptyList())
        session.disconnect()

        assertFalse(connection.acquire())
        connection.release()
        connection.release()
        assertFalse(connection.acquire())
    }

    @Test
    fun proxyFailureReachesTheRemainingTerminal() = withSession { session ->
        val warnings = ForwardWarnings()
        val first = mutableListOf<String>()
        val received = CountDownLatch(1)
        val firstSubscription = warnings.listen(first::add)
        val remaining = warnings.listen { if (it.startsWith("Proxy connection failed:")) received.countDown() }
        val proxy = Socks5Forwarder(session, 0, warnings::report)
        proxy.start()
        try {
            firstSubscription.close()
            val unusedPort = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
            Socket("127.0.0.1", proxy.listenPort).use { socket ->
                socket.soTimeout = 5_000
                val output = socket.getOutputStream()
                val input = socket.getInputStream()
                output.write(byteArrayOf(5, 1, 0))
                output.flush()
                assertArrayEquals(byteArrayOf(5, 0), input.readNBytes(2))
                output.write(byteArrayOf(5, 1, 0, 1, 127, 0, 0, 1,
                    (unusedPort shr 8).toByte(), unusedPort.toByte()))
                output.flush()
                val response = input.readNBytes(10)
                assertEquals(10, response.size)
                assertEquals(5, response[1].toInt())
            }
            assertTrue(received.await(5, TimeUnit.SECONDS))
            assertTrue(first.isEmpty())
        } finally {
            firstSubscription.close()
            remaining.close()
            proxy.stop()
        }
    }

    private fun withSession(check: (Session) -> Unit) {
        val key = System.getenv("LOCHSSH_TEST_SSH_KEY")
        val user = System.getenv("LOCHSSH_TEST_SSH_USER")
        val host = System.getenv("LOCHSSH_TEST_SSH_HOST")
        assumeTrue(!key.isNullOrBlank() && !user.isNullOrBlank() && !host.isNullOrBlank())
        val jsch = JSch().apply {
            setKnownHosts(File(System.getProperty("user.home"), ".ssh/known_hosts").absolutePath)
            addIdentity(requireNotNull(key))
        }
        val session = jsch.getSession(requireNotNull(user), requireNotNull(host), 22)
        session.setConfig("StrictHostKeyChecking", "yes")
        session.setConfig("PreferredAuthentications", "publickey")
        session.connect(5_000)
        try { check(session) }
        finally { session.disconnect() }
    }
}
