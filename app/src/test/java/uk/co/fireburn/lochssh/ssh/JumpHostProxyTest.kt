package uk.co.fireburn.lochssh.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import com.jcraft.jsch.JSchException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assume.assumeTrue
import org.junit.Test

class JumpHostProxyTest {
    @Test
    fun timesOutWhenDestinationDoesNotSendBanner() {
        val key = System.getenv("LOCHSSH_TEST_SSH_KEY")
        val user = System.getenv("LOCHSSH_TEST_SSH_USER")
        assumeTrue(!key.isNullOrBlank() && !user.isNullOrBlank() &&
            System.getenv("LOCHSSH_TEST_SSH_HOST") == "localhost")
        val jsch = JSch()
        jsch.addIdentity(requireNotNull(key))
        jsch.setKnownHosts(File(System.getProperty("user.home"), ".ssh/known_hosts").absolutePath)
        val jump = jsch.getSession(requireNotNull(user), "localhost", 22)
        jump.setConfig("StrictHostKeyChecking", "yes")
        val stop = CountDownLatch(1)
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 5000
            val worker = thread(isDaemon = true) {
                runCatching { server.accept().use { stop.await(5, TimeUnit.SECONDS) } }
            }
            val target = jsch.getSession(user, "127.0.0.1", server.localPort)
            target.setConfig("StrictHostKeyChecking", "yes")
            target.setProxy(JumpHostProxy(jump))
            try {
                jump.connect(5000)
                val started = System.nanoTime()
                try {
                    target.connect(1000)
                    fail("Silent destination connected")
                } catch (error: JSchException) {
                    assertTrue("${error.message}; cause=${error.cause}", error.cause is SocketTimeoutException)
                }
                assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 3000)
                assertFalse(target.isConnected)
                assertTrue(jump.isConnected)
            } finally {
                target.disconnect()
                jump.disconnect()
                stop.countDown()
                worker.join(6000)
            }
        }
    }

    @Test
    fun authenticatesDestinationThroughJump() {
        val key = System.getenv("LOCHSSH_TEST_SSH_KEY")
        val user = System.getenv("LOCHSSH_TEST_SSH_USER")
        val host = System.getenv("LOCHSSH_TEST_SSH_HOST")
        assumeTrue(!key.isNullOrBlank() && !user.isNullOrBlank() && !host.isNullOrBlank())
        val jsch = JSch()
        jsch.addIdentity(requireNotNull(key))
        jsch.setKnownHosts(File(System.getProperty("user.home"), ".ssh/known_hosts").absolutePath)
        val jump = jsch.getSession(requireNotNull(user), requireNotNull(host), 22)
        jump.setConfig("StrictHostKeyChecking", "yes")
        val target = jsch.getSession(user, host, 22)
        target.setConfig("StrictHostKeyChecking", "yes")
        target.setProxy(JumpHostProxy(jump))
        try {
            jump.connect(5000)
            target.connect(5000)
            val exec = target.openChannel("exec") as ChannelExec
            try {
                exec.setCommand("printf lochssh-jump-test")
                val input = exec.inputStream
                exec.connect(5000)
                assertEquals("lochssh-jump-test", input.readBytes().toString(Charsets.UTF_8))
            } finally { exec.disconnect() }
            target.disconnect()
            assertFalse(target.isConnected)
            assertEquals(true, jump.isConnected)
        } finally {
            target.disconnect()
            jump.disconnect()
        }
    }
}
