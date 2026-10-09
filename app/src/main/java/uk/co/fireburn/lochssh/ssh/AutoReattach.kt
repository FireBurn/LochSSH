package uk.co.fireburn.lochssh.ssh

import uk.co.fireburn.lochssh.data.db.RemoteSessionModes

// A dropped connection to a tmux or Screen session is reopened by itself, because the
// session on the host is still running and reattaching loses nothing.
object AutoReattach {
    const val MAX_TRIES = 5
    private val delaysMs = longArrayOf(2_000, 5_000, 10_000, 20_000, 30_000)

    fun applies(remoteCommand: String?, mode: String): Boolean =
        remoteCommand?.let { it.startsWith("tmux ") || it.startsWith("screen ") } == true ||
            mode == RemoteSessionModes.TMUX || mode == RemoteSessionModes.SCREEN

    fun shouldRetry(exitCode: Int): Boolean = exitCode != 0

    fun delayMs(tries: Int): Long? = delaysMs.getOrNull(tries)
}
