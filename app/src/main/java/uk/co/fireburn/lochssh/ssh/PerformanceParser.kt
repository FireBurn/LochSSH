package uk.co.fireburn.lochssh.ssh

object PerformanceParser {
    private val whitespace = Regex("\\s+")
    private val sections = setOf("UPTIME", "CPU", "LOAD", "MEMORY", "NETWORK", "DISK", "TEMPERATURE")
    const val MAX_OUTPUT = 65_536

    fun parse(output: String): PerformanceSnapshot {
        require(output.length <= MAX_OUTPUT) { "Performance readings are too long" }
        val parts = split(output)
        val cpu = cpu(parts["CPU"].orEmpty())
        val memory = memory(parts["MEMORY"].orEmpty())
        require(cpu != null || memory != null) { "Linux performance readings are not available on this server" }
        return PerformanceSnapshot(number(parts["UPTIME"]?.firstOrNull()?.substringBefore(' ')), cpu,
            load(parts["LOAD"].orEmpty()), memory, disk(parts["DISK"].orEmpty()),
            network(parts["NETWORK"].orEmpty()), temperatures(parts["TEMPERATURE"].orEmpty()))
    }

    private fun split(output: String): Map<String, List<String>> {
        val parts = mutableMapOf<String, MutableList<String>>()
        var current: String? = null
        output.lineSequence().forEach { line ->
            if (line.startsWith("LOCHSSH:")) {
                current = line.removePrefix("LOCHSSH:").takeIf { it in sections }
                current?.let { parts.getOrPut(it) { mutableListOf() } }
            } else current?.let { parts.getValue(it).add(line.trim()) }
        }
        return parts
    }

    private fun cpu(lines: List<String>): CpuCounters? {
        val fields = lines.firstOrNull { it.startsWith("cpu ") }?.split(whitespace) ?: return null
        if (fields.size < 5) return null
        // Guest counters are already part of user and nice time.
        val values = fields.drop(1).take(8).map { count(it) ?: return null }
        return CpuCounters(values)
    }

    private fun memory(lines: List<String>): MemoryUse? {
        val values = lines.mapNotNull { line ->
            val fields = line.split(whitespace)
            if (fields.size != 3 || fields[2] != "kB") return@mapNotNull null
            val bytes = bytes(fields[1]) ?: return@mapNotNull null
            fields[0].removeSuffix(":") to bytes
        }.toMap()
        val total = values["MemTotal"]?.takeIf { it > 0 } ?: return null
        val available = values["MemAvailable"] ?: return null
        if (available > total) return null
        return MemoryUse(total, available)
    }

    private fun load(lines: List<String>): List<Double> {
        val fields = lines.firstOrNull()?.split(whitespace) ?: return emptyList()
        if (fields.size < 3) return emptyList()
        return fields.take(3).map { number(it) ?: return emptyList() }
    }

    private fun disk(lines: List<String>): DiskUse? {
        val fields = lines.lastOrNull { it.endsWith(" /") }?.split(whitespace) ?: return null
        if (fields.size < 6) return null
        val total = bytes(fields[fields.size - 5])?.takeIf { it > 0 } ?: return null
        val used = bytes(fields[fields.size - 4]) ?: return null
        val available = bytes(fields[fields.size - 3]) ?: return null
        if (used > total || available > total) return null
        return DiskUse(total, used, available)
    }

    private fun network(lines: List<String>): Map<String, NetworkCounters> = lines.mapNotNull { line ->
        val name = line.substringBefore(':').trim()
        if (':' !in line || name.isBlank() || name.any { it.code < 33 || it.code == 127 }) return@mapNotNull null
        val fields = line.substringAfter(':').trim().split(whitespace)
        if (fields.size < 16) return@mapNotNull null
        val received = count(fields[0]) ?: return@mapNotNull null
        val sent = count(fields[8]) ?: return@mapNotNull null
        name to NetworkCounters(received, sent)
    }.toMap()

    private fun temperatures(lines: List<String>): List<Temperature> = lines.mapNotNull { line ->
        val value = line.substringAfterLast('\t', "").toDoubleOrNull()?.div(1000)
            ?.takeIf { it.isFinite() && it in -100.0..250.0 } ?: return@mapNotNull null
        val name = line.substringBeforeLast('\t').filterNot { it.code < 32 || it.code == 127 }.take(80)
        if (name.isBlank()) null else Temperature(name, value)
    }

    private fun count(value: String): Long? = value.toLongOrNull()?.takeIf { it >= 0 }
    private fun number(value: String?): Double? = value?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
    private fun bytes(value: String): Long? = count(value)?.let {
        if (it > Long.MAX_VALUE / 1024) null else it * 1024
    }
}
