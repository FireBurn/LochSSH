package uk.co.fireburn.lochssh.ui.hosts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
    LaunchedEffect(hostId) { viewModel.load(hostId) }

    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("22") }
    var keepAlive by remember { mutableStateOf("30") }
    var group by remember { mutableStateOf("") }
    var identityId by remember { mutableStateOf<Long?>(null) }
    var identityMenu by remember { mutableStateOf(false) }

    val canSave = name.isNotBlank() && host.isNotBlank()

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
                            viewModel.save(name, host, port, keepAlive, group, identityId)
                            onBack()
                        }
                    ) { Text("Save") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = keepAlive,
                    onValueChange = { keepAlive = it },
                    label = { Text("Keep-alive (s)") },
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

        }
    }
}
