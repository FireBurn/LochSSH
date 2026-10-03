package uk.co.fireburn.lochssh.ssh

data class PerformanceSnapshot(
    val uptimeSeconds: Double?,
    val cpu: CpuCounters?,
    val load: List<Double>,
    val memory: MemoryUse?,
    val disk: DiskUse?,
    val network: Map<String, NetworkCounters>,
    val temperatures: List<Temperature>
)

data class CpuCounters(val values: List<Long>)
data class MemoryUse(val totalBytes: Long, val availableBytes: Long) {
    val usedBytes: Long get() = totalBytes - availableBytes
}
data class DiskUse(val totalBytes: Long, val usedBytes: Long, val availableBytes: Long)
data class NetworkCounters(val receivedBytes: Long, val sentBytes: Long)
data class Temperature(val name: String, val celsius: Double)
data class NetworkRate(val receivedPerSecond: Double, val sentPerSecond: Double)

object PerformanceRates {
    fun cpu(current: CpuCounters?, previous: CpuCounters?): Double? {
        if (current == null || previous == null || current.values.size != previous.values.size) return null
        val changes = current.values.zip(previous.values) { now, before -> now - before }
        if (changes.size < 4 || changes.any { it < 0 }) return null
        val total = changes.sumOf { it.toDouble() }
        if (total <= 0) return null
        val idle = changes[3].toDouble() + changes.getOrElse(4) { 0 }.toDouble()
        return ((total - idle) * 100 / total).coerceIn(0.0, 100.0)
    }

    fun network(current: PerformanceSnapshot, previous: PerformanceSnapshot?): Map<String, NetworkRate> {
        val beforeSnapshot = previous ?: return emptyMap()
        val elapsed = current.uptimeSeconds?.minus(beforeSnapshot.uptimeSeconds ?: return emptyMap())
            ?.takeIf { it > 0 && it.isFinite() } ?: return emptyMap()
        return current.network.mapNotNull { (name, now) ->
            val before = beforeSnapshot.network[name] ?: return@mapNotNull null
            if (now.receivedBytes < before.receivedBytes || now.sentBytes < before.sentBytes) return@mapNotNull null
            val received = (now.receivedBytes - before.receivedBytes) / elapsed
            val sent = (now.sentBytes - before.sentBytes) / elapsed
            if (!received.isFinite() || !sent.isFinite()) return@mapNotNull null
            name to NetworkRate(received, sent)
        }.toMap()
    }
}
