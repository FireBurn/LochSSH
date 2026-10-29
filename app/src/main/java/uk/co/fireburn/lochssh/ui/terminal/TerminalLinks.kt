package uk.co.fireburn.lochssh.ui.terminal

import java.net.URI

object TerminalLinks {
    private const val MIN_CANDIDATE = 10

    private val link = Regex("https?://[^\\s\\p{Cntrl}<>\"']+", RegexOption.IGNORE_CASE)

    fun find(text: String): List<String> = link.findAll(text).mapNotNull { match ->
        val value = trimPunctuation(match.value)
        val uri = runCatching { URI(value) }.getOrNull() ?: return@mapNotNull null
        if (uri.host.isNullOrBlank() || uri.rawUserInfo != null || uri.port !in -1..65535) {
            return@mapNotNull null
        }
        value
    }.distinct().toList()

    // Every address the character at index could belong to. The terminal does not always record where a
    // program broke a line itself, so the text can carry positions where a break may be real or not: the
    // longest address (joined across them) comes first, then the address cut at each of those positions.
    fun candidatesAt(text: String, index: Int, possibleBreaks: List<Int> = emptyList()): List<String> {
        val match = link.findAll(text).firstOrNull { index in it.range } ?: return emptyList()
        val full = find(trimPunctuation(match.value)).firstOrNull() ?: return emptyList()
        val start = match.range.first
        val cuts = possibleBreaks.filter { it > start + MIN_CANDIDATE && it < start + full.length }
            .sortedDescending().mapNotNull { find(full.substring(0, it - start)).firstOrNull() }
        return (listOf(full) + cuts).distinct()
    }

    // The links in text the user selected. A line break inside one may be real or not, so both readings are given.
    fun candidatesIn(text: String): List<String> =
        (find(text.replace("\n", "")) + find(text)).distinct().sortedByDescending { it.length }

    // The link that covers the character at index, if any.
    fun at(text: String, index: Int): String? = link.findAll(text).firstNotNullOfOrNull { match ->
        val value = trimPunctuation(match.value)
        val start = match.range.first
        if (index in start until start + value.length) find(value).firstOrNull() else null
    }

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
