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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import uk.co.fireburn.lochssh.data.db.SshHostEntity
import uk.co.fireburn.lochssh.service.SshForegroundService
import uk.co.fireburn.lochssh.ui.terminal.ReconnectAllButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostListScreen(
    onNewSession: (Long, Long) -> Unit,
    onResumeSession: (Long) -> Unit,
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
    val error by viewModel.error.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<SshHostEntity?>(null) }
    var shortcutError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun findRemoteSessions(host: SshHostEntity) {
        val id = HostRemoteSessions.sessionId(host.id, viewModel.sessions.value, viewModel::newSessionId) ?: return
        viewModel.markFinding(id, host)
        try { SshForegroundService.start(context, host.id, id, findRemoteSessions = true) }
        catch (e: Exception) { viewModel.findFailed(id, e.message ?: "Could not find remote sessions") }
    }

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
                        Text("Sessions", style = MaterialTheme.typography.titleSmall)
                        ReconnectAllButton(sessions.any { it.canReconnect }) {
                            sessions.filter { it.canReconnect }.forEach {
                                SshForegroundService.start(context, it.hostId, it.id, it.remoteCommand)
                            }
                        }
                    }
                }
                items(HostSessionGroups.create(hosts, sessions), key = { "session-host-${it.hostId}" }) { group ->
                    HostSessionGroupView(group, onResumeSession) { SshForegroundService.disconnect(context, it) }
                }
            }
            item(key = "host-actions") {
                Column {
                    Text("Hosts", style = MaterialTheme.typography.titleSmall)
                    HostRemoteSessionsButton(hosts.isNotEmpty()) { hosts.forEach(::findRemoteSessions) }
                }
            }
            items(hosts, key = { it.id }) { host ->
                HostRow(
                    host = host,
                    onClick = { onNewSession(host.id, viewModel.newSessionId()) },
                    onEdit = { onEdit(host.id) },
                    onDuplicate = { viewModel.duplicateHost(host.id) },
                    onDelete = { pendingDelete = host },
                    onShortcut = { scope.launch { shortcutError = HostShortcuts.pin(context, host) } },
                    onFindRemoteSessions = { findRemoteSessions(host) }
                )
            }
        }
    }

    shortcutError?.let { message ->
        AlertDialog(onDismissRequest = { shortcutError = null },
            title = { Text("Shortcut") }, text = { Text(message) },
            confirmButton = { TextButton(onClick = { shortcutError = null }) { Text("Close") } })
    }
    pendingDelete?.let { host ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${host.name}?") },
            text = { Text("This removes the saved host and its port forwards. Open sessions stay connected.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteHost(host.id)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
    error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("Host change failed") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::clearError) { Text("OK") }
            }
        )
    }
}

@Composable
internal fun HostRemoteSessionsButton(enabled: Boolean, onFind: () -> Unit) {
    TextButton(enabled = enabled, onClick = onFind) {
        Text("Find and connect remote sessions on all hosts")
    }
}

@Composable
internal fun HostRow(
    host: SshHostEntity,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onShortcut: () -> Unit,
    onFindRemoteSessions: () -> Unit
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
                    DropdownMenuItem(text = { Text("Find and connect all remote sessions") },
                        onClick = { menuOpen = false; onFindRemoteSessions() })
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menuOpen = false; onEdit() })
                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menuOpen = false; onDuplicate() })
                    DropdownMenuItem(text = { Text("Add to homescreen") }, onClick = { menuOpen = false; onShortcut() })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menuOpen = false; onDelete() })
                }
            }
        }
    }
}
