package uk.co.fireburn.lochssh.ssh

import com.jcraft.jsch.JSch
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class TerminalChannelSshTest {
    @Test
    fun startsTheCommandWithoutTypingItIntoItsInput() {
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
        session.setTimeout(5_000)
        session.connect(5_000)
        val command = "stty raw -echo; printf READY; dd bs=1 count=4 2>/dev/null"
        val channel = TerminalChannel.open(session, command, 80, 24, false)
        try {
            val input = channel.inputStream
            val output = channel.outputStream
            channel.connect()
            assertEquals("READY", input.readNBytes(5).toString(Charsets.UTF_8))
            output.write("ping".toByteArray(Charsets.UTF_8))
            output.flush()
            assertEquals("ping", input.readBytes().toString(Charsets.UTF_8))
        } finally {
            channel.disconnect()
            session.disconnect()
        }
    }
}
