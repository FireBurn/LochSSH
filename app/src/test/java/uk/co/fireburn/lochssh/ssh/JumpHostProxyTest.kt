package uk.co.fireburn.lochssh.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test

class JumpHostProxyTest {
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
