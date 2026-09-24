package uk.co.fireburn.lochssh.ui.hosts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.fireburn.lochssh.data.db.ForwardTypes
import uk.co.fireburn.lochssh.data.db.PortForwardEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostEditorScreen(
    hostId: Long,
    onBack: () -> Unit,
    viewModel: HostEditorViewModel = hiltViewModel()
) {
    val identities by viewModel.identities.collectAsStateWithLifecycle(
        emptyList(),
        LocalLifecycleOwner.current.lifecycle
    )
    val forwards = viewModel.forwards
    LaunchedEffect(hostId) { viewModel.load(hostId) }

    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("22") }
    var keepAlive by remember { mutableStateOf("30") }
    var group by remember { mutableStateOf("") }
    var identityId by remember { mutableStateOf<Long?>(null) }
    var identityMenu by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    var adding by remember { mutableStateOf(false) }
    var draftType by remember { mutableStateOf(ForwardTypes.LOCAL) }
    var draftLocal by remember { mutableStateOf("") }
    var draftRemoteHost by remember { mutableStateOf("localhost") }
    var draftRemote by remember { mutableStateOf("") }

    LaunchedEffect(viewModel.loaded) {
        if (!viewModel.loaded) return@LaunchedEffect
        viewModel.host?.let {
            name = it.name
            host = it.host
            port = it.port.toString()
            keepAlive = it.keepAliveSeconds.toString()
            group = it.group
            identityId = it.identityId
        }
    }

    val validPort = port.toIntOrNull() in 1..65535
    val validKeepAlive = keepAlive.toIntOrNull()?.let { it >= 0 } == true
    val canSave = viewModel.loaded && !saving && name.isNotBlank() && host.isNotBlank() &&
        validPort && validKeepAlive

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (hostId == 0L) "New host" else "Edit host") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        enabled = canSave,
                        onClick = {
                            saving = true
                            saveError = null
                            viewModel.save(name, host, port, keepAlive, group, identityId)
                                .invokeOnCompletion { cause ->
                                    saving = false
                                    if (cause == null) onBack()
                                    else saveError = cause.message ?: "Could not save host"
                                }
                        }
                    ) { Text("Save") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            saveError?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Host") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Port") },
                    isError = port.isNotEmpty() && !validPort,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = keepAlive,
                    onValueChange = { keepAlive = it },
                    label = { Text("Keep-alive (s)") },
                    isError = keepAlive.isNotEmpty() && !validKeepAlive,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = group,
                onValueChange = { group = it },
                label = { Text("Group") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = identities.firstOrNull { it.id == identityId }?.name ?: "None",
                onValueChange = {},
                readOnly = true,
                label = { Text("Identity") },
                trailingIcon = {
                    IconButton(onClick = { identityMenu = true }) {
                        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Pick identity")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            DropdownMenu(expanded = identityMenu, onDismissRequest = { identityMenu = false }) {
                DropdownMenuItem(text = { Text("None") }, onClick = { identityId = null; identityMenu = false })
                identities.forEach { identity ->
                    DropdownMenuItem(
                        text = { Text(identity.name) },
                        onClick = { identityId = identity.id; identityMenu = false }
                    )
                }
            }

            Text("Tunnels", style = MaterialTheme.typography.titleSmall)
            forwards.forEachIndexed { index, f ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        tunnelLabel(f),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    IconButton(onClick = { viewModel.removeForward(index) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove tunnel")
                    }
                }
            }
            if (adding) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = draftType == ForwardTypes.LOCAL,
                        onClick = { draftType = ForwardTypes.LOCAL },
                        label = { Text("Local") }
                    )
                    FilterChip(
                        selected = draftType == ForwardTypes.REMOTE,
                        onClick = { draftType = ForwardTypes.REMOTE },
                        label = { Text("Remote") }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = draftLocal,
                        onValueChange = { draftLocal = it },
                        label = { Text("Local port") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = draftRemote,
                        onValueChange = { draftRemote = it },
                        label = { Text("Remote port") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = draftRemoteHost,
                    onValueChange = { draftRemoteHost = it },
                    label = { Text("Remote host") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        viewModel.addForward(draftType, draftLocal, draftRemoteHost, draftRemote)
                        draftLocal = ""
                        draftRemote = ""
                        draftRemoteHost = "localhost"
                        adding = false
                    }) { Text("Add") }
                    TextButton(onClick = { adding = false }) { Text("Cancel") }
                }
            } else {
                TextButton(onClick = { adding = true }) { Text("Add tunnel") }
            }
        }
    }
}

private fun tunnelLabel(f: PortForwardEntity): String =
    if (f.type == ForwardTypes.LOCAL) {
        "Local ${f.localPort} → ${f.remoteHost}:${f.remotePort}"
    } else {
        "Remote ${f.remotePort} → local ${f.localPort}"
    }
