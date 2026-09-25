package uk.co.fireburn.lochssh.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.fireburn.lochssh.data.db.RemoteSessionModes

class RemoteSessionsTest {
    @Test
    fun readsTmuxSessions() {
        val sessions = RemoteSessions.parseTmux("\$0|work|1\n\$1|editor|0\n")

        assertEquals(2, sessions.size)
        assertEquals(RemoteSessionEntry(RemoteSessionModes.TMUX, "\$0", "work", true), sessions[0])
        assertEquals(RemoteSessionEntry(RemoteSessionModes.TMUX, "\$1", "editor", false), sessions[1])
    }

    @Test
    fun readsScreenSessions() {
        val output = """
            There are screens on:
                1234.work  (Detached)
                5678.editor  (Attached)
            2 Sockets in /run/screen/S-user.
        """.trimIndent()

        val sessions = RemoteSessions.parseScreen(output)

        assertEquals(2, sessions.size)
        assertEquals(RemoteSessionEntry(RemoteSessionModes.SCREEN, "1234.work", "work", false), sessions[0])
        assertEquals(RemoteSessionEntry(RemoteSessionModes.SCREEN, "5678.editor", "editor", true), sessions[1])
    }

    @Test
    fun ignoresUnexpectedIdentifiers() {
        assertTrue(RemoteSessions.parseTmux("bad|work|1\n").isEmpty())
        assertTrue(RemoteSessions.parseScreen("1234.a;exit  (Detached)").isEmpty())
    }

    @Test
    fun offersSafeCommands() {
        val tmux = RemoteSessionEntry(RemoteSessionModes.TMUX, "\$0", "work", false)
        val screen = RemoteSessionEntry(RemoteSessionModes.SCREEN, "1234.work", "work", true)

        assertEquals("tmux attach-session -t '\$0'", RemoteSessions.attach(tmux))
        assertEquals("screen -d -r '1234.work'", RemoteSessions.attach(screen))
        assertEquals("tmux new-session -A -s lochssh", RemoteSessions.automatic(RemoteSessionModes.TMUX))
        assertEquals("screen -d -RR", RemoteSessions.automatic(RemoteSessionModes.SCREEN))
    }
}
