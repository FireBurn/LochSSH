package uk.co.fireburn.lochssh.ui.security

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uk.co.fireburn.lochssh.security.LockStatus

@Composable
fun AppLockGate(status: LockStatus, onUnlock: () -> Unit, onClose: () -> Unit,
    content: @Composable () -> Unit) {
    if (!status.locked) { content(); return }
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("LochSSH is locked", style = MaterialTheme.typography.headlineSmall)
        Text("Unlock with your device screen lock. Open SSH connections stay running.")
        status.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TextButton(enabled = !status.waiting, onClick = onUnlock) {
            Text(if (status.waiting) "Waiting for device unlock…" else "Unlock")
        }
        TextButton(onClick = onClose) { Text("Close") }
    }
}
