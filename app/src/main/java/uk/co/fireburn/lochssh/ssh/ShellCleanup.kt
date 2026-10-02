package uk.co.fireburn.lochssh.ssh

import uk.co.fireburn.lochssh.data.db.RemoteSessionModes
import uk.co.fireburn.lochssh.ui.terminal.TerminalBuffer

internal object ShellCleanup {
    fun canClose(config: SshConnectionConfig, command: String?, hasInput: Boolean,
        buffer: TerminalBuffer): Boolean {
        if (command != null || hasInput || config.autoCommand.isNotBlank() ||
            config.remoteSessionMode in setOf(RemoteSessionModes.TMUX, RemoteSessionModes.SCREEN)) return false
        return buffer.read { !buffer.isAlternateScreen && !buffer.mouseTracking }
    }
}
