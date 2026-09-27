package uk.co.fireburn.lochssh.ui.terminal

import java.net.URI

object TerminalLinks {
    private val link = Regex("https?://[^\\s\\p{Cntrl}<>\"']+", RegexOption.IGNORE_CASE)

    fun find(text: String): List<String> = link.findAll(text).mapNotNull { match ->
        var value = match.value.trimEnd('.', ',', ';', ':', '!', '?')
        for ((open, close) in listOf('(' to ')', '[' to ']', '{' to '}')) {
            while (value.endsWith(close) && value.count { it == close } > value.count { it == open }) {
                value = value.dropLast(1)
            }
        }
        val uri = runCatching { URI(value) }.getOrNull() ?: return@mapNotNull null
        if (uri.host.isNullOrBlank() || uri.rawUserInfo != null || uri.port !in -1..65535) {
            return@mapNotNull null
        }
        value
    }.distinct().toList()
}
