package uk.co.fireburn.lochssh.ssh

// The tmux or Screen session a terminal is attached to, read from the command that
// attached it. The terminal's own scrollback is empty or unreliable inside these, so the
// app scrolls the session itself.
data class Multiplexer(val tool: Tool, val target: String?) {
    enum class Tool { TMUX, SCREEN }

    fun scrollUp(lines: Int): String = "tmux copy-mode -e -t ${quote()} 2>/dev/null; " +
        "tmux send-keys -t ${quote()} -X -N $lines scroll-up 2>/dev/null; " + inMode()

    fun scrollDown(lines: Int): String =
        "tmux send-keys -t ${quote()} -X -N $lines scroll-down 2>/dev/null; " + inMode()

    fun cancel(): String = "tmux send-keys -t ${quote()} -X cancel 2>/dev/null; " + inMode()

    fun mode(): String = inMode()

    // Whether this is the session a scan found, by id or by name.
    fun refersTo(entry: RemoteSessionEntry): Boolean {
        val wanted = target ?: return false
        val sameTool = (tool == Tool.TMUX) == (entry.tool == uk.co.fireburn.lochssh.data.db.RemoteSessionModes.TMUX)
        return sameTool && (wanted == entry.id || (entry.sessionName.isNotEmpty() &&
            (wanted == entry.sessionName || wanted.substringAfter('.') == entry.sessionName)))
    }

    private fun inMode() = "tmux display-message -p -t ${quote()} '#{pane_in_mode}'"

    private fun quote() = "'" + requireNotNull(target).replace("'", "'\\''") + "'"

    companion object {
        private val tmuxTarget = Regex("""(?:attach-session|attach|a)\s+(?:-\S+\s+)*-t\s+'?([^'\s]+)'?""")
        private val tmuxName = Regex("""-s\s+'?([^'\s]+)'?""")
        private val screenName = Regex("""-S\s+'?([^'\s]+)'?""")
        private val screenAttach = Regex("""-[rx]\s+'([^']+)'""")

        fun parse(command: String?): Multiplexer? {
            val text = command?.trim() ?: return null
            return when {
                text.startsWith("tmux ") -> {
                    val target = tmuxTarget.find(text)?.groupValues?.get(1)
                        ?: tmuxName.find(text)?.groupValues?.get(1)
                    target?.let { Multiplexer(Tool.TMUX, it) }
                }
                text.startsWith("screen ") -> Multiplexer(Tool.SCREEN,
                    screenName.find(text)?.groupValues?.get(1) ?: screenAttach.find(text)?.groupValues?.get(1))
                else -> null
            }
        }
    }
}
