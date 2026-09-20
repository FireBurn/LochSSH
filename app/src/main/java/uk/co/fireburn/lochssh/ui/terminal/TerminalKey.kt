package uk.co.fireburn.lochssh.ui.terminal

sealed class TerminalKey {
    data class Text(val value: String) : TerminalKey()
    data class Arrow(val final: Char) : TerminalKey() // A=up B=down C=right D=left
    data object Esc : TerminalKey()
    data object Tab : TerminalKey()
    data object Home : TerminalKey()
    data object End : TerminalKey()
    data object PgUp : TerminalKey()
    data object PgDn : TerminalKey()
    data object Ctrl : TerminalKey()
    data object Alt : TerminalKey()
    data object Fn : TerminalKey()
    data object ImeToggle : TerminalKey()
}

object TerminalKeyEncoder {
    // Returns null for keys the UI handles itself (modifier toggles, IME).
    fun encode(key: TerminalKey, ctrl: Boolean, alt: Boolean, fn: Boolean): ByteArray? =
        when (key) {
            is TerminalKey.Text -> {
                val v = key.value
                when {
                    ctrl && v.length == 1 -> {
                        val c = v.lowercase().single()
                        if (c in 'a'..'z') byteArrayOf((c.code - 96).toByte()) else null
                    }
                    fn || alt -> byteArrayOf(0x1B.toByte()) + v.toByteArray()
                    else -> v.toByteArray()
                }
            }
            is TerminalKey.Arrow -> {
                val mod = (if (ctrl) 4 else 0) + (if (alt) 2 else 0)
                val suffix = if (mod == 0) key.final.toString() else "1;${mod + 1}${key.final}"
                seq(suffix)
            }
            TerminalKey.Esc -> byteArrayOf(0x1B.toByte())
            TerminalKey.Tab -> byteArrayOf(0x09)
            TerminalKey.Home -> seq("H")
            TerminalKey.End -> seq("F")
            TerminalKey.PgUp -> seq("5~")
            TerminalKey.PgDn -> seq("6~")
            TerminalKey.Ctrl,
            TerminalKey.Alt,
            TerminalKey.Fn,
            TerminalKey.ImeToggle -> null
        }

    private fun seq(s: String) =
        byteArrayOf(0x1B.toByte(), '['.code.toByte()) + s.toByteArray()
}
