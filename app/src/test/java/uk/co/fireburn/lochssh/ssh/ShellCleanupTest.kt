package uk.co.fireburn.lochssh.ssh

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.fireburn.lochssh.data.db.AuthTypes
import uk.co.fireburn.lochssh.data.db.RemoteSessionModes
import uk.co.fireburn.lochssh.ui.terminal.TerminalBuffer

class ShellCleanupTest {
    private val config = SshConnectionConfig("test.invalid", 22, "test", AuthTypes.NONE,
        null, null, null, null, 30)
    private val buffer = TerminalBuffer(80, 24)

    private fun feed(text: String) {
        val bytes = text.toByteArray()
        buffer.feed(bytes, bytes.size)
    }

    @Test fun acceptsUntouchedShellsIncludingDiscoveryConnections() {
        feed("Welcome\r\n$ ")
        for (mode in listOf(RemoteSessionModes.SHELL, RemoteSessionModes.ASK, RemoteSessionModes.CONNECT_ALL)) {
            assertTrue(ShellCleanup.canClose(config.copy(remoteSessionMode = mode), null, false, buffer))
        }
    }

    @Test fun preservesShellsWithInputOrStartupCommands() {
        assertFalse(ShellCleanup.canClose(config, null, true, buffer))
        assertFalse(ShellCleanup.canClose(config, "tmux attach-session", false, buffer))
        assertFalse(ShellCleanup.canClose(config.copy(autoCommand = "top"), null, false, buffer))
    }

    @Test fun preservesAutomaticRemoteSessions() {
        for (mode in listOf(RemoteSessionModes.TMUX, RemoteSessionModes.SCREEN)) {
            assertFalse(ShellCleanup.canClose(config.copy(remoteSessionMode = mode), null, false, buffer))
        }
    }

    @Test fun preservesProgramsStartedByLoginFiles() {
        for (mode in listOf(47, 1047, 1049)) {
            feed("\u001B[?$mode" + "h")
            assertFalse(ShellCleanup.canClose(config, null, false, buffer))
            feed("\u001B[?$mode" + "l")
            assertTrue(ShellCleanup.canClose(config, null, false, buffer))
        }
    }

    @Test fun preservesMouseUsingProgramsOnTheNormalScreen() {
        feed("\u001B[?1000h")
        assertFalse(ShellCleanup.canClose(config, null, false, buffer))
        feed("\u001B[?1000l")
        assertTrue(ShellCleanup.canClose(config, null, false, buffer))
    }
}
