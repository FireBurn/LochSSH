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
import uk.co.fireburn.lochssh.ssh.SavedSshProxy
import uk.co.fireburn.lochssh.data.db.SshHostEntity

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
    var jumpHostId by remember(hostId) { mutableStateOf<Long?>(null) }
    var jumpMenu by remember(hostId) { mutableStateOf(false) }
    var agentForwarding by remember(hostId) { mutableStateOf(false) }
    var telnet by remember(hostId) { mutableStateOf(false) }
    var localShell by remember(hostId) { mutableStateOf(false) }
    var tmuxSeen by remember(hostId) { mutableStateOf(false) }
    var screenSeen by remember(hostId) { mutableStateOf(false) }
    var confirmAgent by remember(hostId) { mutableStateOf(false) }
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

    var name by remember(hostId) { mutableStateOf("") }
    var host by remember(hostId) { mutableStateOf("") }
    var port by remember(hostId) { mutableStateOf("22") }
    var keepAlive by remember(hostId) { mutableStateOf("30") }
    var group by remember(hostId) { mutableStateOf("") }
    var autoCommand by remember(hostId) { mutableStateOf("") }
    var knockSequence by remember(hostId) { mutableStateOf("") }
    var knockPause by remember(hostId) { mutableStateOf("200") }
    var remoteSessionMode by remember(hostId) { mutableStateOf(RemoteSessionModes.SHELL) }
    var identityId by remember(hostId) { mutableStateOf<Long?>(null) }
    var identityMenu by remember(hostId) { mutableStateOf(false) }
    var saveError by remember(hostId) { mutableStateOf<String?>(null) }
    var saving by remember(hostId) { mutableStateOf(false) }
    var proxy by remember(hostId) { mutableStateOf(HostProxyFields()) }
    var confirmProxy by remember(hostId) { mutableStateOf(false) }

    var adding by remember(hostId) { mutableStateOf(false) }
    var draftType by remember(hostId) { mutableStateOf(ForwardTypes.LOCAL) }
    var draftLocal by remember(hostId) { mutableStateOf("") }
    var draftRemoteHost by remember(hostId) { mutableStateOf("localhost") }
    var draftRemote by remember(hostId) { mutableStateOf("") }

    LaunchedEffect(hostId, viewModel.loaded, viewModel.host) {
        if (!viewModel.loaded) return@LaunchedEffect
        viewModel.host?.let {
            if (it.id != hostId) return@let
            name = it.name
            host = it.host
            port = it.port.toString()
            keepAlive = it.keepAliveSeconds.toString()
            group = it.group
            autoCommand = it.autoCommand
            knockSequence = it.knockSequence
            knockPause = it.knockPause.toString()
            remoteSessionMode = it.remoteSessionMode
            identityId = it.identityId
            jumpHostId = it.jumpHostId
            agentForwarding = it.agentForwarding
            telnet = it.telnet
            localShell = it.localShell
            tmuxSeen = it.tmuxSeen
            screenSeen = it.screenSeen
            proxy = HostProxyFields.from(it)
        }
    }

    val validPort = port.toIntOrNull() in 1..65535
    val validKeepAlive = KeepAlive.parse(keepAlive) != null
    val validCommand = autoCommand.none { it == '\n' || it == '\r' }
    val knockError = runCatching {
        uk.co.fireburn.lochssh.ssh.KnockSequence.parse(knockSequence)
        require(knockSequence.isBlank() || knockPause.toLongOrNull() in 0L..5000L) { "Pause must be between 0 and 5000 milliseconds" }
        require(jumpHostId == null || knockSequence.isBlank()) { "Configure knocking on the first jump host instead" }
    }.exceptionOrNull()?.message
    val proxyError = runCatching {
        val candidate = proxy.applyTo(SshHostEntity(name = name, host = host,
            jumpHostId = jumpHostId, knockSequence = knockSequence))
        require(jumpHostId == null || candidate.proxyType == SavedSshProxy.NONE) {
            "Set the proxy on the first jump host, not on a host reached through SSH"
        }
        SavedSshProxy.checkRoute(listOf(candidate))
    }.exceptionOrNull()?.message
    val canSave = viewModel.loaded && !saving && name.isNotBlank() && host.isNotBlank() &&
        validPort && validKeepAlive && validCommand && knockError == null && proxyError == null
    val minListenPort = if (draftType == ForwardTypes.REMOTE) 1 else 1024
    val validDraftLocal = draftLocal.toIntOrNull() in minListenPort..65535
    val validDraftRemote = draftType == ForwardTypes.DYNAMIC ||
        draftRemote.toIntOrNull() in 1..65535
    val validDraftHost = draftType != ForwardTypes.LOCAL || draftRemoteHost.isNotBlank()
    val canAddTunnel = viewModel.loaded && validDraftLocal && validDraftRemote && validDraftHost

    fun save() {
        if (!canSave) return
        saving = true
        saveError = null
        viewModel.save(name, host, port, keepAlive, group, identityId, autoCommand, remoteSessionMode,
            jumpHostId, agentForwarding, knockSequence, knockPause, proxy, telnet, localShell) { error ->
            saving = false
            if (error == null) onBack() else saveError = error
        }
    }

    if (confirmProxy) {
        ProxyLoginWarning(onConfirm = { confirmProxy = false; save() }, onDismiss = { confirmProxy = false })
    }

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
                            if (proxy.identityId != null && proxy.type != SavedSshProxy.NONE)
                                confirmProxy = true else save()
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
            viewModel.loadError?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { viewModel.load(hostId) }) { Text("Retry") }
            }
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
            OutlinedTextField(value = knockSequence, onValueChange = { knockSequence = it },
                label = { Text("Port knocking") }, singleLine = true,
                isError = knockError != null,
                supportingText = { Text(knockError ?: "Optional: 7000,8000:udp,9000:tcp. Leave blank to disable. Direct connections only.") },
                modifier = Modifier.fillMaxWidth())
            if (knockSequence.isNotBlank()) {
                OutlinedTextField(value = knockPause, onValueChange = { knockPause = it },
                    label = { Text("Pause between knocks (milliseconds)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
            }
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
                listOfNotNull(
                    (RemoteSessionModes.TMUX to "Auto tmux").takeIf { tmuxSeen || remoteSessionMode == RemoteSessionModes.TMUX },
                    (RemoteSessionModes.SCREEN to "Auto screen").takeIf { screenSeen || remoteSessionMode == RemoteSessionModes.SCREEN }
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = remoteSessionMode == mode,
                        onClick = { remoteSessionMode = mode },
                        label = { Text(label) }
                    )
                }
            }
            Text(
                "Ask lists remote sessions after connecting. Auto tmux and Auto screen appear " +
                    "once the host has been seen to have that tool. Auto tmux resumes or creates " +
                    "a session named lochssh. Auto screen resumes the first available session " +
                    "or creates one. A dropped connection to one of these reconnects by itself.",
                style = MaterialTheme.typography.bodySmall
            )
            FilterChip(selected = remoteSessionMode == RemoteSessionModes.CONNECT_ALL,
                onClick = { remoteSessionMode = RemoteSessionModes.CONNECT_ALL },
                label = { Text("Find and connect all remote sessions") })
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
                    Text("Use Telnet")
                    Text("Unencrypted: text and passwords can be read on the network. No tunnels, jump hosts or proxy.",
                        style = MaterialTheme.typography.bodySmall)
                }
                androidx.compose.material3.Switch(checked = telnet, onCheckedChange = {
                    telnet = it
                    if (it) localShell = false
                    if (it && port == "22") port = "23" else if (!it && port == "23") port = "22"
                })
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Run a shell on this device")
                    Text("Starts the Android shell here, with no network. Typing is edited by the app, so full-screen programs do not work.",
                        style = MaterialTheme.typography.bodySmall)
                }
                androidx.compose.material3.Switch(checked = localShell, onCheckedChange = {
                    localShell = it
                    if (it) telnet = false
                })
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
            HostProxySettings(proxy, identities, { proxy = it }, proxyError)
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
