package uk.co.fireburn.lochssh.ssh

object ExitCodes {
    // The channel can be reported closed before its exit status arrives, so a closed channel on a
    // link that is still up means the remote program ended, not that the connection was lost.
    fun of(failed: Boolean, channelClosed: Boolean, status: Int, linkUp: Boolean): Int = when {
        failed -> AutoReattach.LOST
        channelClosed && status >= 0 -> status
        channelClosed && linkUp -> 0
        else -> AutoReattach.LOST
    }
}
