package uk.co.fireburn.lochssh.ui.terminal

object TerminalKeypad {
    fun encode(symbol: Char, application: Boolean, numLock: Boolean,
        modifier: Int = 1, applicationCursor: Boolean = false): ByteArray {
        val final = when (symbol) {
            in '0'..'9' -> ('p'.code + symbol.code - '0'.code).toChar()
            '*' -> 'j'
            '+' -> 'k'
            ',' -> 'l'
            '-' -> 'm'
            '.' -> 'n'
            '/' -> 'o'
            '=' -> 'X'
            '\r' -> 'M'
            else -> null
        }
        if (application && !numLock && final != null) {
            val parameter = if (modifier == 1) "" else modifier.toString()
            return "\u001BO$parameter$final".toByteArray(Charsets.US_ASCII)
        }
        if (!numLock) {
            val navigation = when (symbol) {
                '0' -> "2~"
                '1' -> "F"
                '2' -> "B"
                '3' -> "6~"
                '4' -> "D"
                '5' -> "E"
                '6' -> "C"
                '7' -> "H"
                '8' -> "A"
                '9' -> "5~"
                '.' -> "3~"
                else -> null
            }
            if (navigation != null) return TerminalKeyEncoder.navigation(navigation, modifier, applicationCursor)
        }
        return requireNotNull(TerminalKeyEncoder.encode(TerminalKey.Text(symbol.toString()),
            (modifier - 1) and 4 != 0, (modifier - 1) and 2 != 0, false))
    }
}
