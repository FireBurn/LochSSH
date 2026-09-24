package uk.co.fireburn.lochssh.ssh

import com.jcraft.jsch.JSch
import java.io.File
import java.net.Socket
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class Socks5ForwarderTest {
    @Test
    fun forwardsAnSshBannerThroughSocks5() {
        val key = System.getenv("LOCHSSH_TEST_SSH_KEY")
        val user = System.getenv("LOCHSSH_TEST_SSH_USER")
        val host = System.getenv("LOCHSSH_TEST_SSH_HOST")
        assumeTrue(!key.isNullOrBlank() && !user.isNullOrBlank() && !host.isNullOrBlank())
        val keyPath = requireNotNull(key)
        val username = requireNotNull(user)
        val hostname = requireNotNull(host)
        val home = requireNotNull(System.getProperty("user.home"))

        val jsch = JSch()
        jsch.setKnownHosts(File(home, ".ssh/known_hosts").absolutePath)
        jsch.addIdentity(keyPath)
        val session = jsch.getSession(username, hostname, 22)
        session.setConfig("StrictHostKeyChecking", "yes")
        session.connect(5_000)
        val forwarder = Socks5Forwarder(session, 0)
        try {
            forwarder.start()
            Socket("127.0.0.1", forwarder.listenPort).use { socket ->
                socket.soTimeout = 5_000
                val output = socket.getOutputStream()
                val input = socket.getInputStream()
                output.write(byteArrayOf(5, 1, 0))
                output.flush()
                assertArrayEquals(byteArrayOf(5, 0), input.readNBytes(2))
                output.write(byteArrayOf(5, 1, 0, 1, 127, 0, 0, 1, 0, 22))
                output.flush()
                assertEquals(0, input.readNBytes(10)[1].toInt())
                assertArrayEquals("SSH-".toByteArray(), input.readNBytes(4))
            }
        } finally {
            forwarder.stop()
            session.disconnect()
        }
    }
}
