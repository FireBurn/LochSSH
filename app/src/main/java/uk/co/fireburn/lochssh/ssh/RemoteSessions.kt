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
    fun startupOptions(mode: String, available: (String) -> Boolean,
        discover: () -> RemoteSessionOptions): RemoteSessionOptions = when (mode) {
        RemoteSessionModes.TMUX -> RemoteSessionOptions(tmuxAvailable = available(mode))
        RemoteSessionModes.SCREEN -> RemoteSessionOptions(screenAvailable = available(mode))
        else -> discover()
    }

    fun availabilityCommand(tool: String): String {
        val name = when (tool) {
            RemoteSessionModes.TMUX -> "tmux"
            RemoteSessionModes.SCREEN -> "screen"
            else -> throw IllegalArgumentException("Unknown remote session tool")
        }
        return "command -v $name >/dev/null 2>&1"
    }

    private val tmuxId = Regex("\\$[0-9]+")
    private val screenId = Regex("[0-9]+\\.[^\\x00-\\x1f\\x7f/]+")
    private val screenStatus = Regex("\\((Attached|Detached|Multi, attached|Multi, detached)\\)\\s*$")
    private val screenDate = Regex("\\s+\\([0-9][^()]*\\)\\s*$")
    private val screenDateField = Regex("\\([0-9][^()]*\\)")

    fun parseTmux(output: String): List<RemoteSessionEntry> = output.lineSequence().mapNotNull { line ->
        val first = line.indexOf('|')
        val last = line.lastIndexOf('|')
        if (first <= 0 || last <= first || !tmuxId.matches(line.substring(0, first))) {
            return@mapNotNull null
        }
        val attached = line.substring(last + 1).toIntOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
        RemoteSessionEntry(
            RemoteSessionModes.TMUX,
            line.substring(0, first),
            line.substring(first + 1, last).filterNot { it.code < 32 || it.code == 127 },
            attached > 0
        )
    }.toList()

    fun parseScreen(output: String): List<RemoteSessionEntry> = output.lineSequence().mapNotNull { line ->
        val status = screenStatus.find(line) ?: return@mapNotNull null
        if (status.range.first == 0 || !line[status.range.first - 1].isWhitespace()) return@mapNotNull null
        val beforeStatus = line.substring(0, status.range.first).trimStart(' ', '\t')
        val id = if ('\t' in beforeStatus) {
            val extra = beforeStatus.substringAfter('\t').trim()
            if (extra.isNotEmpty() && !screenDateField.matches(extra)) return@mapNotNull null
            beforeStatus.substringBefore('\t')
        } else {
            beforeStatus.replace(screenDate, "").trim()
        }
        if (!screenId.matches(id)) return@mapNotNull null
        RemoteSessionEntry(
            RemoteSessionModes.SCREEN,
            id,
            id.substringAfter('.'),
            status.groupValues[1] == "Attached" || status.groupValues[1] == "Multi, attached"
        )
    }.toList()

    fun attach(entry: RemoteSessionEntry): String = when (entry.tool) {
        RemoteSessionModes.TMUX -> "tmux attach-session -t ${quote(entry.id)}"
        RemoteSessionModes.SCREEN -> {
            val action = if (entry.attached) "-d -r" else "-r"
            "screen -A $action ${quote(entry.id)}"
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
        RemoteSessionModes.SCREEN -> "screen -A -d -RR"
        else -> throw IllegalArgumentException("Unknown remote session tool")
    }

    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
