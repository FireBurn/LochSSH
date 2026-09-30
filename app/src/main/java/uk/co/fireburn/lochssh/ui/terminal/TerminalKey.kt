package uk.co.fireburn.lochssh.ui.terminal

sealed class TerminalKey {
    data class Text(val value: String) : TerminalKey()
    data class Arrow(val final: Char) : TerminalKey() // A=up B=down C=right D=left
    data object Esc : TerminalKey()
    data object Tab : TerminalKey()
    data object Enter : TerminalKey()
    data object Home : TerminalKey()
    data object End : TerminalKey()
    data object PgUp : TerminalKey()
    data object PgDn : TerminalKey()
    data object Backspace : TerminalKey()
    data object Del : TerminalKey()
    data object Ctrl : TerminalKey()
    data object Alt : TerminalKey()
    data object Fn : TerminalKey()
    data object ImeToggle : TerminalKey()
}

object TerminalKeyEncoder {
    // Returns null for keys the UI handles itself (modifier toggles, IME).
    fun encode(key: TerminalKey, ctrl: Boolean, alt: Boolean, fn: Boolean,
        applicationCursor: Boolean = false): ByteArray? =
        when (key) {
            is TerminalKey.Text -> {
                val v = key.value
                val content = if (ctrl && v.length == 1) {
                    val c = v.single().uppercaseChar()
                    when {
                        c == '?' -> byteArrayOf(0x7F)
                        c == ' ' -> byteArrayOf(0)
                        c in '@'..'_' -> byteArrayOf((c.code and 31).toByte())
                        else -> v.toByteArray()
                    }
                } else v.toByteArray()
                if (fn || alt) {
                    byteArrayOf(0x1B.toByte()) + content
                } else {
                    content
                }
            }
            is TerminalKey.Arrow -> {
                navigation(key.final.toString(), ctrl, alt, applicationCursor)
            }
            TerminalKey.Esc -> byteArrayOf(0x1B.toByte())
            TerminalKey.Tab -> byteArrayOf(0x09)
            TerminalKey.Enter -> byteArrayOf(0x0D)
            TerminalKey.Home -> navigation("H", ctrl, alt, applicationCursor)
            TerminalKey.End -> navigation("F", ctrl, alt, applicationCursor)
            TerminalKey.PgUp -> navigation("5~", ctrl, alt, applicationCursor)
            TerminalKey.PgDn -> navigation("6~", ctrl, alt, applicationCursor)
            TerminalKey.Backspace -> byteArrayOf(0x7F)
            TerminalKey.Del -> navigation("3~", ctrl, alt, applicationCursor)
            TerminalKey.Ctrl,
            TerminalKey.Alt,
            TerminalKey.Fn,
            TerminalKey.ImeToggle -> null
        }

    fun navigation(key: String, modifier: Int = 1, applicationCursor: Boolean = false): ByteArray {
        if (modifier == 1 && applicationCursor && key.length == 1) {
            return "\u001BO$key".toByteArray()
        }
        val suffix = when {
            modifier == 1 -> key
            key.endsWith('~') -> "${key.dropLast(1)};$modifier~"
            else -> "1;$modifier$key"
        }
        return seq(suffix)
    }

    private fun navigation(key: String, ctrl: Boolean, alt: Boolean, applicationCursor: Boolean) =
        navigation(key, 1 + (if (ctrl) 4 else 0) + (if (alt) 2 else 0), applicationCursor)

    private fun seq(s: String) =
        byteArrayOf(0x1B.toByte(), '['.code.toByte()) + s.toByteArray()
}
