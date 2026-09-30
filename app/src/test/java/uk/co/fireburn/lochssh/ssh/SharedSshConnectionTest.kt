package uk.co.fireburn.lochssh.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.io.File
import java.io.IOException
import java.net.Socket
import org.junit.Assert.assertEquals
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

            connection.release()

            assertFalse(session.isConnected)
            assertFalse(connection.acquire())
            assertThrows(IOException::class.java) { Socket("127.0.0.1", localPort).close() }
            assertThrows(IOException::class.java) { Socket("127.0.0.1", proxyPort).close() }
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
