package uk.co.fireburn.lochssh.ui.terminal

object PasteText {
    // Escape and other control characters are dropped: inside a bracketed paste an escape sequence could end
    // the paste early, and outside one it would be run as a command. Line breaks and tabs are kept.
    fun clean(text: String): String = buildString(text.length) {
        for (char in text.replace("\r\n", "\n").replace('\r', '\n')) {
            if (char == '\n' || char == '\t' || (char.code >= 32 && char.code != 127 && char !in '\u0080'..'\u009F')) append(char)
        }
    }

    fun lineCount(text: String): Int = if (text.isEmpty()) 0 else text.count { it == '\n' } + 1

    // The first few lines, shortened, so what is about to be run can be checked.
    fun preview(text: String, lines: Int = 8, width: Int = 80): String {
        val all = clean(text).lines()
        val shown = all.take(lines).joinToString("\n") { if (it.length > width) it.take(width) + "…" else it }
        return if (all.size > lines) shown + "\n… and ${all.size - lines} more" else shown
    }
}
