package uk.co.fireburn.lochssh.ui.hosts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun HostSessionGroupView(group: HostSessionGroup, onResume: (Long) -> Unit,
    onDisconnect: (Long) -> Unit, onNewSession: () -> Unit = {}, onRescan: () -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(group.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                group.address?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            TextButton(onClick = onNewSession) { Text("New tmux") }
            TextButton(onClick = onRescan) { Text("Rescan") }
        }
        group.sessions.forEach { session -> key(session.id) {
            Card(onClick = { onResume(session.id) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(session.label)
                        Text(session.statusText, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(session.actionLabel, style = MaterialTheme.typography.labelLarge)
                    if (session.manager == null) {
                        IconButton(onClick = { onDisconnect(session.id) }) {
                            Icon(Icons.Filled.Close, contentDescription =
                                if (session.error == null) "Cancel connection" else "Dismiss")
                        }
                    }
                }
            }
        } }
    }
}
