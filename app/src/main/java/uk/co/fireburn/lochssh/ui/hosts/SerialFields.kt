package uk.co.fireburn.lochssh.ui.hosts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import uk.co.fireburn.lochssh.serial.SerialSettings
import uk.co.fireburn.lochssh.serial.UsbSerial

@Composable
internal fun SerialFields(
    baud: String, onBaud: (String) -> Unit,
    bits: Int, onBits: (Int) -> Unit,
    parity: SerialSettings.Parity, onParity: (SerialSettings.Parity) -> Unit,
    stop: SerialSettings.StopBits, onStop: (SerialSettings.StopBits) -> Unit,
    flow: SerialSettings.Flow, onFlow: (SerialSettings.Flow) -> Unit,
    device: String, onDevice: (String) -> Unit,
    error: String?
) {
    val context = LocalContext.current
    var attached by remember { mutableStateOf<List<UsbSerial.Attached>>(emptyList()) }
    LaunchedEffect(Unit) { attached = runCatching { UsbSerial.attached(context) }.getOrDefault(emptyList()) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = baud, onValueChange = onBaud, label = { Text("Baud rate") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Chips("Data bits", (5..8).toList(), bits, onBits) { it.toString() }
        Chips("Parity", SerialSettings.Parity.entries, parity, onParity) { it.name.lowercase() }
        Chips("Stop bits", SerialSettings.StopBits.entries, stop, onStop) {
            when (it) {
                SerialSettings.StopBits.ONE -> "1"
                SerialSettings.StopBits.ONE_AND_HALF -> "1.5"
                SerialSettings.StopBits.TWO -> "2"
            }
        }
        Chips("Flow control", SerialSettings.Flow.entries, flow, onFlow) {
            when (it) {
                SerialSettings.Flow.NONE -> "none"
                SerialSettings.Flow.RTS_CTS -> "RTS/CTS"
                SerialSettings.Flow.XON_XOFF -> "XON/XOFF"
            }
        }
        OutlinedTextField(value = device, onValueChange = onDevice,
            label = { Text("USB device (blank for the first adapter)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth())
        Text(
            if (attached.isEmpty()) "No USB serial adapter is plugged in right now."
            else "Plugged in: " + attached.joinToString("; ") { it.label },
            style = MaterialTheme.typography.bodySmall
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun <T> Chips(title: String, options: List<T>, selected: T, onSelect: (T) -> Unit, label: (T) -> String) {
    Column {
        Text(title, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { FilterChip(selected = it == selected, onClick = { onSelect(it) }, label = { Text(label(it)) }) }
        }
    }
}
