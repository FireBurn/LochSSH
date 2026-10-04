package uk.co.fireburn.lochssh.ui.terminal

import java.net.URI

object TerminalLinks {
    private val link = Regex("https?://[^\\s\\p{Cntrl}<>\"']+", RegexOption.IGNORE_CASE)

    fun find(text: String): List<String> = link.findAll(text).mapNotNull { match ->
        var value = match.value.trimEnd('.', ',', ';', ':', '!', '?')
        for ((open, close) in listOf('(' to ')', '[' to ']', '{' to '}')) {
            var excess = value.count { it == close } - value.count { it == open }
            var end = value.length
            while (excess > 0 && end > 0 && value[end - 1] == close) { end--; excess-- }
            if (end != value.length) value = value.substring(0, end)
        }
        val uri = runCatching { URI(value) }.getOrNull() ?: return@mapNotNull null
        if (uri.host.isNullOrBlank() || uri.rawUserInfo != null || uri.port !in -1..65535) {
            return@mapNotNull null
        }
        value
    }.distinct().toList()
}
