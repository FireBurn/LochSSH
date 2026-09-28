package uk.co.fireburn.lochssh.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect

@Composable
fun BatterySettingsCard() {
    val context = LocalContext.current
    var exempt by remember(context) { mutableStateOf<Boolean?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LifecycleResumeEffect(context) {
        exempt = BatterySettings.exemption(context)
        onPauseOrDispose { }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Screen-off connections", style = MaterialTheme.typography.titleMedium)
            Text(when (exempt) {
                true -> "Exempt from Android battery restrictions"
                false -> "Android battery restrictions apply"
                null -> "Could not read the battery setting"
            })
            Text("Android can pause network access with the screen off. Allowing background access may use more battery. Other system limits can still close connections.",
                style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { error = BatterySettings.open(context, requestExemption = exempt == false) }) {
                Text(if (exempt == false) "Request battery exemption" else "Battery settings")
            }
        }
    }
    error?.let { message ->
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("Could not open battery settings") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } }
        )
    }
}
