package uk.co.fireburn.lochssh.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import uk.co.fireburn.lochssh.ui.terminal.TerminalSession

class TerminalRepliesSshTest {
    @Test
    fun remoteProgramReceivesTheCursorReport() {
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
        val channel = session.openChannel("exec") as ChannelExec
        try {
            channel.setPty(true)
            channel.setCommand("stty raw -echo; printf '\\033[6n'; dd bs=1 count=6 2>/dev/null | od -An -tx1")
            val input = channel.inputStream
            val output = channel.outputStream
            val terminal = TerminalSession(80, 24) { reply ->
                output.write(reply.toByteArray(Charsets.US_ASCII))
                output.flush()
            }
            channel.connect(3_000)
            val request = input.readNBytes(4)
            assertEquals("\u001B[6n", request.toString(Charsets.US_ASCII))
            terminal.feed(request, request.size)

            assertEquals("1b 5b 31 3b 31 52", input.readBytes().toString(Charsets.US_ASCII).trim())
        } finally {
            channel.disconnect()
            session.disconnect()
        }
    }
}
