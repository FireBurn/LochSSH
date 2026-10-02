package uk.co.fireburn.lochssh.ui.terminal

object TerminalMouse {
    fun wheel(up: Boolean, column: Int, row: Int, sgr: Boolean): ByteArray {
        val button = if (up) 64 else 65
        if (sgr) return "\u001B[<$button;${column.coerceAtLeast(0) + 1};${row.coerceAtLeast(0) + 1}M"
            .toByteArray(Charsets.US_ASCII)
        return byteArrayOf(27, 91, 77, (button + 32).toByte(),
            (column.coerceIn(0, 222) + 33).toByte(), (row.coerceIn(0, 222) + 33).toByte())
    }
}
