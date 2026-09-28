package uk.co.fireburn.lochssh.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class AgentForwardingTest {
    @Test
    fun authenticatesRemoteSshUsingOnlyForwardedKey() {
        val key = System.getenv("LOCHSSH_TEST_SSH_KEY")
        val user = System.getenv("LOCHSSH_TEST_SSH_USER")
        assumeTrue(!key.isNullOrBlank() && !user.isNullOrBlank() &&
            System.getenv("LOCHSSH_TEST_SSH_HOST") == "localhost")
        val jsch = JSch()
        jsch.addIdentity(requireNotNull(key))
        jsch.setKnownHosts(File(System.getProperty("user.home"), ".ssh/known_hosts").absolutePath)
        jsch.setIdentityRepository(ForwardedIdentityRepository(jsch.identityRepository))
        val session = jsch.getSession(requireNotNull(user), "localhost", 22)
        session.setConfig("StrictHostKeyChecking", "yes")
        try {
            session.connect(5000)
            val exec = session.openChannel("exec") as ChannelExec
            try {
                exec.setAgentForwarding(true)
                exec.setCommand("ssh -F /dev/null -o BatchMode=yes -o IdentityFile=none " +
                    "-o PreferredAuthentications=publickey -o StrictHostKeyChecking=yes " +
                    "-o ConnectTimeout=3 localhost printf lochssh-agent-test")
                val input = exec.inputStream
                exec.connect(5000)
                assertEquals("lochssh-agent-test", input.readBytes().toString(Charsets.UTF_8))
            } finally { exec.disconnect() }
        } finally {
            session.disconnect()
            jsch.removeAllIdentity()
        }
    }
}
