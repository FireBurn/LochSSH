package uk.co.fireburn.lochssh.ssh

import uk.co.fireburn.lochssh.data.db.RemoteSessionModes

data class RemoteSessionEntry(
    val tool: String,
    val id: String,
    val name: String,
    val attached: Boolean
)

data class RemoteSessionOptions(
    val tmuxAvailable: Boolean = false,
    val screenAvailable: Boolean = false,
    val sessions: List<RemoteSessionEntry> = emptyList(),
    val error: String? = null
)

object RemoteSessions {
    private val tmuxId = Regex("\\$[0-9]+")
    private val screenId = Regex("[0-9]+\\.[A-Za-z0-9_.-]+")
    private val screenLine = Regex("^\\s*([0-9]+\\.[A-Za-z0-9_.-]+)\\s+\\((Attached|Detached)\\)")

    fun parseTmux(output: String): List<RemoteSessionEntry> = output.lineSequence().mapNotNull { line ->
        val first = line.indexOf('|')
        val last = line.lastIndexOf('|')
        if (first <= 0 || last <= first || !tmuxId.matches(line.substring(0, first))) {
            return@mapNotNull null
        }
        RemoteSessionEntry(
            RemoteSessionModes.TMUX,
            line.substring(0, first),
            line.substring(first + 1, last).filterNot { it.code < 32 || it.code == 127 },
            line.substring(last + 1) == "1"
        )
    }.toList()

    fun parseScreen(output: String): List<RemoteSessionEntry> = output.lineSequence().mapNotNull { line ->
        val match = screenLine.find(line) ?: return@mapNotNull null
        val id = match.groupValues[1]
        if (!screenId.matches(id)) return@mapNotNull null
        RemoteSessionEntry(
            RemoteSessionModes.SCREEN,
            id,
            id.substringAfter('.'),
            match.groupValues[2] == "Attached"
        )
    }.toList()

    fun attach(entry: RemoteSessionEntry): String = when (entry.tool) {
        RemoteSessionModes.TMUX -> "tmux attach-session -t ${quote(entry.id)}"
        RemoteSessionModes.SCREEN -> {
            val action = if (entry.attached) "-d -r" else "-r"
            "screen $action ${quote(entry.id)}"
        }
        else -> throw IllegalArgumentException("Unknown remote session tool")
    }

    fun create(tool: String): String = when (tool) {
        RemoteSessionModes.TMUX -> "tmux new-session"
        RemoteSessionModes.SCREEN -> "screen"
        else -> throw IllegalArgumentException("Unknown remote session tool")
    }

    fun automatic(tool: String): String = when (tool) {
        RemoteSessionModes.TMUX -> "tmux new-session -A -s lochssh"
        RemoteSessionModes.SCREEN -> "screen -d -RR"
        else -> throw IllegalArgumentException("Unknown remote session tool")
    }

    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
