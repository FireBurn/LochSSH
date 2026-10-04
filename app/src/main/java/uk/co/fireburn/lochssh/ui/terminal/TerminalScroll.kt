package uk.co.fireburn.lochssh.ui.terminal

internal object TerminalScroll {
    fun remoteInput(buffer: TerminalBuffer, up: Boolean, column: Int, row: Int,
        scrollOffset: Int): ByteArray? = buffer.read {
        when {
            scrollOffset != 0 -> null
            buffer.mouseTracking -> TerminalMouse.wheel(up, column, row, buffer.mouseSgr, buffer.mouseUtf8)
            buffer.alternateScroll && buffer.isAlternateScreen ->
                TerminalKeyEncoder.navigation(if (up) "A" else "B", applicationCursor = buffer.applicationCursor)
            else -> null
        }
    }
}
