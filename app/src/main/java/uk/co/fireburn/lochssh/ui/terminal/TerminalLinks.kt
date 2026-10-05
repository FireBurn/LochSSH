package uk.co.fireburn.lochssh.ui.terminal

import java.net.URI

object TerminalLinks {
    private val link = Regex("https?://[^\\s\\p{Cntrl}<>\"']+", RegexOption.IGNORE_CASE)

    fun find(text: String): List<String> = link.findAll(text).mapNotNull { match ->
        val value = trimPunctuation(match.value)
        val uri = runCatching { URI(value) }.getOrNull() ?: return@mapNotNull null
        if (uri.host.isNullOrBlank() || uri.rawUserInfo != null || uri.port !in -1..65535) {
            return@mapNotNull null
        }
        value
    }.distinct().toList()

    private fun trimPunctuation(value: String): String {
        val excess = IntArray(3)
        for (char in value) {
            when (char) {
                '(' -> excess[0]--
                ')' -> excess[0]++
                '[' -> excess[1]--
                ']' -> excess[1]++
                '{' -> excess[2]--
                '}' -> excess[2]++
            }
        }
        var end = value.length
        while (end > 0) {
            val char = value[end - 1]
            val bracket = when (char) { ')' -> 0; ']' -> 1; '}' -> 2; else -> -1 }
            if (bracket >= 0 && excess[bracket] > 0) excess[bracket]--
            else if (char !in ".,;:!?") break
            end--
        }
        return value.substring(0, end)
    }
}
