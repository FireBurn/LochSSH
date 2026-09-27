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
    fun recognizesTmuxSessionsWithMultipleClients() {
        assertTrue(RemoteSessions.parseTmux("\$0|work|2\n").single().attached)
        assertTrue(RemoteSessions.parseTmux("\$0|work|invalid\n").isEmpty())
        assertTrue(RemoteSessions.parseTmux("\$0|work|-1\n").isEmpty())
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
        assertTrue(RemoteSessions.parseScreen("1234.a/b  (Detached)").isEmpty())
        assertTrue(RemoteSessions.parseScreen("1234.a\u001b  (Detached)").isEmpty())
        assertTrue(RemoteSessions.parseScreen("1234.work  (Dead)").isEmpty())
        assertTrue(RemoteSessions.parseScreen("1234.work  (Detached) trailing text").isEmpty())
        assertTrue(RemoteSessions.parseScreen("\t1234.work\tunexpected\t(Detached)").isEmpty())
    }

    @Test
    fun readsNamesWithSpacesAndNonAsciiCharacters() {
        val session = RemoteSessions.parseScreen("\t3313517.lochssh test+é\t(Detached)\r\n").single()

        assertEquals("3313517.lochssh test+é", session.id)
        assertEquals("lochssh test+é", session.name)
        assertEquals("1234.work (123)", RemoteSessions.parseScreen("\t1234.work (123)\t(Detached)").single().id)
    }

    @Test
    fun readsMultiUserAndDatedListings() {
        val sessions = RemoteSessions.parseScreen(
            "\t1234.work\t(Multi, attached)\n" +
                "\t5678.editor\t(09/27/2026 10:00:00 PM)\t(Multi, detached)\n" +
                "9012.build  (09/27/2026 10:00:00 PM)  (Detached)\n"
        )

        assertEquals(3, sessions.size)
        assertTrue(sessions[0].attached)
        assertEquals("5678.editor", sessions[1].id)
        assertEquals("9012.build", sessions[2].id)
    }

    @Test
    fun quotesShellCharactersInScreenNames() {
        val session = RemoteSessions.parseScreen("\t1234.work';echo bad\t(Detached)\n").single()

        assertEquals("screen -r '1234.work'\\'';echo bad'", RemoteSessions.attach(session))
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
