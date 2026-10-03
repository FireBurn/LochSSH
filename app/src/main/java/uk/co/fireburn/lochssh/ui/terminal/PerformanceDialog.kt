package uk.co.fireburn.lochssh.ui.terminal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runInterruptible
import uk.co.fireburn.lochssh.ssh.PerformanceRates
import uk.co.fireburn.lochssh.ssh.PerformanceSnapshot
import uk.co.fireburn.lochssh.ssh.NetworkRate
import java.util.Locale

private data class PerformanceReading(val snapshot: PerformanceSnapshot, val cpu: Double?,
    val network: Map<String, NetworkRate>, val history: List<Double>)

@Composable
fun PerformanceDialog(hostName: String, read: () -> PerformanceSnapshot, onClose: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current
    val currentRead by rememberUpdatedState(read)
    var reading by remember(hostName) { mutableStateOf<PerformanceReading?>(null) }
    var error by remember(hostName) { mutableStateOf<String?>(null) }
    var retry by remember(hostName) { mutableIntStateOf(0) }
    LaunchedEffect(hostName, lifecycle, retry) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous: PerformanceSnapshot? = null
            var history = emptyList<Double>()
            reading = null
            error = null
            while (isActive) {
                try {
                    val snapshot = runInterruptible(Dispatchers.IO) { currentRead() }
                    val cpu = PerformanceRates.cpu(snapshot.cpu, previous?.cpu)
                    if (cpu != null) history = (history + cpu).takeLast(30)
                    reading = PerformanceReading(snapshot, cpu, PerformanceRates.network(snapshot, previous), history)
                    previous = snapshot
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    error = failure.message?.take(200) ?: "Could not read server performance"
                    break
                }
                delay(2_000)
            }
        }
    }
    AlertDialog(onDismissRequest = onClose, title = { Text("Performance · $hostName") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Linux server readings. Updates while this view is open.")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (error != null && reading != null) Text("Last successful reading")
                val values = reading
                if (values != null) PerformanceValues(values)
                else if (error == null) Text("Reading server performance…")
            }
        }, confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
        dismissButton = {
            if (error != null) TextButton(onClick = { retry++ }) { Text("Retry") }
        })
}

@Composable
private fun PerformanceValues(reading: PerformanceReading) {
    val snapshot = reading.snapshot
    Text("CPU: ${reading.cpu?.let { percent(it) } ?: if (snapshot.cpu != null) "Measuring…" else "Unavailable"}")
    if (reading.history.size > 1) CpuHistory(reading.history)
    Text("Load (1, 5, 15 minutes): ${snapshot.load.takeIf { it.size == 3 }?.joinToString(" / ") { decimal(it) } ?: "Unavailable"}")
    Text("Memory: ${snapshot.memory?.let { "${bytes(it.usedBytes.toDouble())} used / ${bytes(it.totalBytes.toDouble())}" } ?: "Unavailable"}")
    Text("Root disk: ${snapshot.disk?.let { "${bytes(it.usedBytes.toDouble())} used / ${bytes(it.totalBytes.toDouble())}, ${bytes(it.availableBytes.toDouble())} available" } ?: "Unavailable"}")
    Text("Network", style = MaterialTheme.typography.titleSmall)
    if (snapshot.network.isEmpty()) Text("Unavailable")
    snapshot.network.forEach { (name, _) ->
        val rate = reading.network[name]
        Text("$name: ${rate?.let { "↓ ${bytes(it.receivedPerSecond)}/s  ↑ ${bytes(it.sentPerSecond)}/s" } ?: "Measuring…"}")
    }
    Text("Temperatures", style = MaterialTheme.typography.titleSmall)
    if (snapshot.temperatures.isEmpty()) Text("Unavailable")
    snapshot.temperatures.forEach { Text("${it.name}: ${decimal(it.celsius)} °C") }
}

@Composable
private fun CpuHistory(values: List<Double>) {
    val colour = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(64.dp).semantics { contentDescription = "Recent CPU use, 0 to 100 percent" }) {
        fun point(index: Int) = Offset(index * size.width / (values.size - 1),
            size.height * (1 - values[index].toFloat() / 100))
        for (index in 1 until values.size) drawLine(colour, point(index - 1), point(index), strokeWidth = 2.dp.toPx())
    }
}

private fun decimal(value: Double) = String.format(Locale.ROOT, "%.1f", value)
private fun percent(value: Double) = "${decimal(value)}%"
private fun bytes(value: Double): String {
    val units = listOf("B", "KiB", "MiB", "GiB", "TiB", "PiB", "EiB")
    var amount = value
    var unit = 0
    while (amount >= 1024 && unit < units.lastIndex) { amount /= 1024; unit++ }
    return "${decimal(amount)} ${units[unit]}"
}
