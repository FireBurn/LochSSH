package uk.co.fireburn.lochssh.ui.hosts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.fireburn.lochssh.data.db.SshHostEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostListScreen(
    onOpenSession: (Long, Long) -> Unit,
    onEdit: (Long) -> Unit,
    onNew: () -> Unit,
    onIdentities: () -> Unit,
    onSettings: () -> Unit,
    viewModel: HostsViewModel = hiltViewModel()
) {
    val hosts by viewModel.hosts.collectAsStateWithLifecycle(
        emptyList(),
        LocalLifecycleOwner.current.lifecycle
    )
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LochSSH") },
                actions = {
                    IconButton(onClick = onIdentities) {
                        Icon(Icons.Filled.Lock, contentDescription = "Identities")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNew) {
                Icon(Icons.Filled.Add, contentDescription = "New host")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (sessions.isNotEmpty()) {
                item(key = "sessions") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Open sessions", style = MaterialTheme.typography.titleSmall)
                        sessions.forEach { session ->
                            Card(
                                onClick = { onOpenSession(session.hostId, session.id) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(session.label)
                                        Text(
                                            session.error ?: "Connected",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Text("Resume", style = MaterialTheme.typography.labelLarge)
                                    if (session.manager == null) {
                                        IconButton(onClick = { viewModel.dismissSession(session.id) }) {
                                            Icon(Icons.Filled.Close, contentDescription = "Dismiss")
                                        }
                                    }
                                }
                            }
                        }
                        Text("Hosts", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            items(hosts, key = { it.id }) { host ->
                HostRow(
                    host = host,
                    onClick = { onOpenSession(host.id, viewModel.newSessionId()) },
                    onEdit = { onEdit(host.id) },
                    onDuplicate = { viewModel.duplicateHost(host.id) },
                    onDelete = { viewModel.deleteHost(host.id) }
                )
            }
        }
    }
}

@Composable
private fun HostRow(
    host: SshHostEntity,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(host.name, style = MaterialTheme.typography.titleMedium)
                val subtitle = buildString {
                    append("${host.host}:${host.port}")
                    if (host.group.isNotBlank()) append(" · ${host.group}")
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Actions")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menuOpen = false; onEdit() })
                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menuOpen = false; onDuplicate() })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menuOpen = false; onDelete() })
                }
            }
        }
    }
}
