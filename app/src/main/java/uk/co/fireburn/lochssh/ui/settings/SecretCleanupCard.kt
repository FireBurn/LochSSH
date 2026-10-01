package uk.co.fireburn.lochssh.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun SecretCleanupCard(error: String?, running: Boolean, onRetry: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Stored secret cleanup", style = MaterialTheme.typography.titleMedium)
            Text("Retry removing unused secrets left after a failed save or deletion.")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = onRetry, enabled = !running) {
                Text(if (running) "Checking…" else "Retry cleanup")
            }
        }
    }
}
