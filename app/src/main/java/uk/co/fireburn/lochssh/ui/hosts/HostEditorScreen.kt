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
import uk.co.fireburn.lochssh.data.db.RemoteSessionModes
import uk.co.fireburn.lochssh.ssh.KeepAlive

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
    val hosts by viewModel.hosts.collectAsStateWithLifecycle(emptyList())
    var jumpHostId by remember { mutableStateOf<Long?>(null) }
    var jumpMenu by remember { mutableStateOf(false) }
    var agentForwarding by remember { mutableStateOf(false) }
    var confirmAgent by remember { mutableStateOf(false) }
    if (confirmAgent) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmAgent = false },
            title = { Text("Forward this key to the remote host?") },
            text = { Text("The remote host can use this connection's key to authenticate elsewhere while connected. The private key stays on the phone, but a compromised host can still use it. Enable only for hosts you trust. A private-key identity is required.") },
            confirmButton = { TextButton(onClick = {
                agentForwarding = true
                confirmAgent = false
            }) { Text("Enable") } },
            dismissButton = { TextButton(onClick = { confirmAgent = false }) { Text("Cancel") } }
        )
    }
    LaunchedEffect(hostId) { viewModel.load(hostId) }

    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("22") }
    var keepAlive by remember { mutableStateOf("30") }
    var group by remember { mutableStateOf("") }
    var autoCommand by remember { mutableStateOf("") }
    var remoteSessionMode by remember { mutableStateOf(RemoteSessionModes.SHELL) }
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
            autoCommand = it.autoCommand
            remoteSessionMode = it.remoteSessionMode
            identityId = it.identityId
            jumpHostId = it.jumpHostId
            agentForwarding = it.agentForwarding
        }
    }

    val validPort = port.toIntOrNull() in 1..65535
    val validKeepAlive = KeepAlive.parse(keepAlive) != null
    val validCommand = autoCommand.none { it == '\n' || it == '\r' }
    val canSave = viewModel.loaded && !saving && name.isNotBlank() && host.isNotBlank() &&
        validPort && validKeepAlive && validCommand
    val minListenPort = if (draftType == ForwardTypes.REMOTE) 1 else 1024
    val validDraftLocal = draftLocal.toIntOrNull() in minListenPort..65535
    val validDraftRemote = draftType == ForwardTypes.DYNAMIC ||
        draftRemote.toIntOrNull() in 1..65535
    val validDraftHost = draftType != ForwardTypes.LOCAL || draftRemoteHost.isNotBlank()
    val canAddTunnel = validDraftLocal && validDraftRemote && validDraftHost

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
                            if (saving) return@TextButton
                            saving = true
                            saveError = null
                            viewModel.save(
                                name, host, port, keepAlive, group, identityId,
                                autoCommand, remoteSessionMode, jumpHostId, agentForwarding
                            ) { error ->
                                saving = false
                                if (error == null) onBack() else saveError = error
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
                    supportingText = {
                        Text(if (validKeepAlive) "0 turns keep-alive off"
                            else "Use 0–${KeepAlive.MAX_SECONDS} seconds")
                    },
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
                value = autoCommand,
                onValueChange = { autoCommand = it },
                label = { Text("Run on connect") },
                supportingText = { Text("Used when Remote sessions is Shell") },
                isError = !validCommand,
                enabled = remoteSessionMode == RemoteSessionModes.SHELL,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text("Remote sessions", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    RemoteSessionModes.SHELL to "Shell",
                    RemoteSessionModes.ASK to "Ask"
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = remoteSessionMode == mode,
                        onClick = { remoteSessionMode = mode },
                        label = { Text(label) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    RemoteSessionModes.TMUX to "Auto tmux",
                    RemoteSessionModes.SCREEN to "Auto screen"
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = remoteSessionMode == mode,
                        onClick = { remoteSessionMode = mode },
                        label = { Text(label) }
                    )
                }
            }
            Text(
                "Ask lists remote sessions after connecting. Auto tmux resumes or creates " +
                    "a session named lochssh. Auto screen resumes the first available session " +
                    "or creates one.",
                style = MaterialTheme.typography.bodySmall
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

            OutlinedTextField(
                value = hosts.firstOrNull { it.id == jumpHostId }?.name
                    ?: if (jumpHostId == null) "Direct connection" else "Missing jump host",
                onValueChange = {}, readOnly = true, label = { Text("Jump host") },
                trailingIcon = {
                    IconButton(onClick = { jumpMenu = true }) {
                        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Pick jump host")
                    }
                }, modifier = Modifier.fillMaxWidth()
            )
            DropdownMenu(expanded = jumpMenu, onDismissRequest = { jumpMenu = false }) {
                DropdownMenuItem(text = { Text("Direct connection") },
                    onClick = { jumpHostId = null; jumpMenu = false })
                hosts.filter { it.id != hostId }.forEach { jump ->
                    DropdownMenuItem(text = { Text(jump.name) },
                        onClick = { jumpHostId = jump.id; jumpMenu = false })
                }
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Forward SSH agent")
                    Text("Share this connection's key for remote SSH authentication",
                        style = MaterialTheme.typography.bodySmall)
                }
                androidx.compose.material3.Switch(checked = agentForwarding,
                    onCheckedChange = { if (it) confirmAgent = true else agentForwarding = false })
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
                    FilterChip(
                        selected = draftType == ForwardTypes.DYNAMIC,
                        onClick = { draftType = ForwardTypes.DYNAMIC },
                        label = { Text("SOCKS5") }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = draftLocal,
                        onValueChange = { draftLocal = it },
                        label = { Text(if (draftType == ForwardTypes.REMOTE) "Local service port" else "Listen port") },
                        isError = draftLocal.isNotEmpty() && !validDraftLocal,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    if (draftType != ForwardTypes.DYNAMIC) {
                        OutlinedTextField(
                            value = draftRemote,
                            onValueChange = { draftRemote = it },
                            label = { Text(if (draftType == ForwardTypes.REMOTE) "Server port" else "Destination port") },
                            isError = draftRemote.isNotEmpty() && !validDraftRemote,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (draftType == ForwardTypes.LOCAL) {
                    OutlinedTextField(
                        value = draftRemoteHost,
                        onValueChange = { draftRemoteHost = it },
                        label = { Text("Destination host") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (draftType == ForwardTypes.DYNAMIC) {
                    Text("The SOCKS5 listener is available to other apps on this device.")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        enabled = canAddTunnel,
                        onClick = {
                            if (viewModel.addForward(draftType, draftLocal, draftRemoteHost, draftRemote)) {
                                draftLocal = ""
                                draftRemote = ""
                                draftRemoteHost = "localhost"
                                adding = false
                            }
                        }
                    ) { Text("Add") }
                    TextButton(onClick = { adding = false }) { Text("Cancel") }
                }
            } else {
                TextButton(onClick = { adding = true }) { Text("Add tunnel") }
            }
        }
    }
}

private fun tunnelLabel(f: PortForwardEntity): String = when (f.type) {
    ForwardTypes.LOCAL -> "Local ${f.localPort} → ${f.remoteHost}:${f.remotePort}"
    ForwardTypes.REMOTE -> "Remote ${f.remotePort} → local ${f.localPort}"
    ForwardTypes.DYNAMIC -> "SOCKS5 localhost:${f.localPort}"
    else -> "Unknown tunnel"
}
