package uk.co.fireburn.lochssh.ui.terminal

enum class TerminalTheme(val label: String, val foreground: Int, val background: Int, val cursor: Int) {
    LOCH("Loch", 0xFFE0E1DD.toInt(), 0xFF0D1B2A.toInt(), 0xFFE9C46A.toInt()),
    BLACK("Black", 0xFFE8E8E8.toInt(), 0xFF000000.toInt(), 0xFFE8E8E8.toInt()),
    PAPER("Paper", 0xFF202020.toInt(), 0xFFF5F2E9.toInt(), 0xFF8A4700.toInt());

    fun color(value: Int): Int = when (value) {
        TerminalBuffer.DEFAULT_FG -> foreground
        TerminalBuffer.DEFAULT_BG -> background
        else -> value
    }

    companion object {
        fun decode(value: String?): TerminalTheme = entries.firstOrNull { it.name == value } ?: LOCH
    }
}
