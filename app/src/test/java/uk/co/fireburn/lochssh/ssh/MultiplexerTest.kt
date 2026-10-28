package uk.co.fireburn.lochssh.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MultiplexerTest {
    @Test
    fun readsTheTargetFromTheCommandThatAttached() {
        assertEquals(Multiplexer(Multiplexer.Tool.TMUX, "\$0"),
            Multiplexer.parse(RemoteSessions.attach(RemoteSessionEntry("TMUX", "\$0", "x", false))))
        assertEquals(Multiplexer(Multiplexer.Tool.TMUX, "lochssh"),
            Multiplexer.parse("tmux new-session -A -s lochssh"))
        assertEquals(Multiplexer(Multiplexer.Tool.TMUX, "loch42"),
            Multiplexer.parse(RemoteSessions.create("TMUX", "loch42")))
        assertEquals(Multiplexer.Tool.SCREEN, Multiplexer.parse("screen -A -d -RR")?.tool)
        assertEquals(Multiplexer.Tool.SCREEN, Multiplexer.parse(RemoteSessions.create("SCREEN", "n"))?.tool)
    }

    @Test
    fun otherCommandsAreNotMultiplexers() {
        assertNull(Multiplexer.parse(null))
        assertNull(Multiplexer.parse("ls -la"))
        assertNull(Multiplexer.parse("tmux list-sessions"))
    }

    @Test
    fun tmuxCommandsQuoteTheTarget() {
        val mux = Multiplexer(Multiplexer.Tool.TMUX, "\$1")
        assertEquals("tmux send-keys -t '\$1' -X -N 3 scroll-down 2>/dev/null; " +
            "tmux display-message -p -t '\$1' '#{pane_in_mode}'", mux.scrollDown(3))
    }

    @Test
    fun automaticModePrefersTmuxThenScreen() {
        val both = RemoteSessionOptions(tmuxAvailable = true, screenAvailable = true)
        val screenOnly = RemoteSessionOptions(screenAvailable = true)
        assertEquals("tmux new-session -A -s lochssh", RemoteSessions.startupCommand("AUTO", both))
        assertEquals("screen -A -d -RR", RemoteSessions.startupCommand("AUTO", screenOnly))
        assertNull(RemoteSessions.startupCommand("AUTO", RemoteSessionOptions()))
    }

    @Test
    fun newSessionUsesTmuxUnlessOnlyScreenWasSeen() {
        assertEquals(true, RemoteSessions.createPreferred(false, false).startsWith("tmux "))
        assertEquals(true, RemoteSessions.createPreferred(true, true).startsWith("tmux "))
        assertEquals(true, RemoteSessions.createPreferred(false, true).startsWith("screen "))
    }
}
