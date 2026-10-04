package uk.co.fireburn.lochssh.ui.terminal

object TerminalMouse {
    fun wheel(up: Boolean, column: Int, row: Int, sgr: Boolean, utf8: Boolean = false): ByteArray {
        val button = if (up) 64 else 65
        if (sgr) return "\u001B[<$button;${column.coerceAtLeast(0).toLong() + 1};${row.coerceAtLeast(0).toLong() + 1}M"
            .toByteArray(Charsets.US_ASCII)
        if (utf8) return ("\u001B[M" + (button + 32).toChar() +
            (column.coerceIn(0, 2014) + 33).toChar() + (row.coerceIn(0, 2014) + 33).toChar())
            .toByteArray(Charsets.UTF_8)
        return byteArrayOf(27, 91, 77, (button + 32).toByte(),
            (column.coerceIn(0, 222) + 33).toByte(), (row.coerceIn(0, 222) + 33).toByte())
    }
}
