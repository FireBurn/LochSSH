package uk.co.fireburn.lochssh.ssh

internal object RemoteTasks {
    const val command = "ps -e -o pid=,ppid=,pgid=,tpgid=,comm= 2>/dev/null"
    private data class Process(val id: Long, val parent: Long, val group: Long, val foreground: Long, val name: String)
    private val wrappers = setOf("sudo", "su", "bash", "sh", "zsh", "fish", "dash", "doas")

    fun names(output: String, panePid: Long): String? {
        val processes = output.lineSequence().take(8192).mapNotNull { line ->
            val fields = line.trim().split(Regex("\\s+"), limit = 5)
            if (fields.size != 5) return@mapNotNull null
            val numbers = fields.take(4).map { it.toLongOrNull() ?: return@mapNotNull null }
            val name = fields[4].filterNot { it.code < 32 || it.code == 127 }.take(64)
            Process(numbers[0], numbers[1], numbers[2], numbers[3], name)
        }.associateBy { it.id }
        val children = processes.values.groupBy { it.parent }
        val depths = mutableMapOf(panePid to 0)
        var level = listOf(panePid)
        repeat(64) {
            level = level.flatMap { children[it].orEmpty() }.filter { it.id !in depths }
                .map { process -> depths[process.id] = it + 1; process.id }
            if (level.isEmpty()) return@repeat
        }
        val foreground = depths.keys.mapNotNull { processes[it] }
            .filter { it.group > 0 && it.group == it.foreground && it.name !in wrappers }
            .maxWithOrNull(compareBy<Process> { depths[it.id] ?: 0 }.thenBy { it.id }) ?: return null
        val names = mutableListOf<String>()
        var current: Process? = foreground
        repeat(64) {
            val process = current ?: return@repeat
            if (process.id == panePid) { current = null; return@repeat }
            if (process.name !in wrappers && process.name.isNotBlank()) names.add(process.name)
            current = processes[process.parent]?.takeIf { it.id in depths }
        }
        return names.asReversed().distinct().take(4).joinToString(" · ").takeIf { it.isNotBlank() }
    }

    fun enrich(tmux: String, processes: String): String = tmux.lineSequence().joinToString("\n") { line ->
        val end = line.lastIndexOf('|')
        val start = line.indexOf('|')
        if (start < 0 || end <= start) return@joinToString line
        val fields = line.substring(start + 1, end).split('\t', limit = 5)
        if (fields.size < 4) return@joinToString line
        val task = fields[3].toLongOrNull()?.takeIf { it > 0 }?.let { names(processes, it) }.orEmpty()
        val labels = if (task.isBlank()) fields.take(3) else listOf(fields[0], task, fields[2])
        val title = fields.getOrNull(4)?.replace('\t', ' ').orEmpty()
        line.substring(0, start + 1) + (labels + title).joinToString("\t") + line.substring(end)
    }
}
