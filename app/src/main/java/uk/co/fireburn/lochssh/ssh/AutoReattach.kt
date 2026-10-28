package uk.co.fireburn.lochssh.ssh

// A connection that drops without being closed is opened again by itself. If it was in
// tmux or Screen, the same command attaches the session that kept running on the host.
object AutoReattach {
    private val delaysMs = longArrayOf(500, 2_000, 5_000, 10_000, 20_000, 30_000, 30_000, 30_000)
    val MAX_TRIES = delaysMs.size

    fun applies(localShell: Boolean): Boolean = !localShell

    fun shouldRetry(exitCode: Int): Boolean = exitCode != 0

    fun delayMs(tries: Int): Long? = delaysMs.getOrNull(tries)
}
