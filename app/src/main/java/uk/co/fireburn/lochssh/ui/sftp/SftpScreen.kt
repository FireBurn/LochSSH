package uk.co.fireburn.lochssh.ui.sftp

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.fireburn.lochssh.ssh.RemoteFile
import uk.co.fireburn.lochssh.ssh.RemotePath

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SftpScreen(
    sessionId: Long,
    onBack: () -> Unit,
    viewModel: SftpViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedDownload by remember { mutableStateOf<RemoteFile?>(null) }
    val downloadPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val file = selectedDownload
        if (uri != null && file != null) viewModel.download(file, uri)
        selectedDownload = null
    }
    val uploadPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        uri -> if (uri != null) viewModel.prepareUpload(uri)
    }

    LaunchedEffect(sessionId) { viewModel.load(sessionId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Remote files") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.browse(state.path) }, enabled = !state.busy) {
                        Text("Refresh")
                    }
                    TextButton(onClick = { uploadPicker.launch(arrayOf("*/*")) }, enabled = !state.busy) {
                        Text("Upload")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Text(state.path, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            state.error?.let { Text(it, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
            state.message?.let { Text(it, modifier = Modifier.padding(16.dp)) }
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RemotePath.parent(state.path)?.let { parent ->
                    item(key = "parent") {
                        Card(
                            onClick = { viewModel.browse(parent) },
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("..  Parent folder", modifier = Modifier.padding(16.dp))
                        }
                    }
                }
                items(state.files, key = { it.name }) { file ->
                    Card(
                        onClick = {
                            if (file.isDirectory) {
                                viewModel.browse(RemotePath.child(state.path, file.name))
                            } else {
                                selectedDownload = file
                                downloadPicker.launch(file.name)
                            }
                        },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(file.name, style = MaterialTheme.typography.titleMedium)
                            val detail = when {
                                file.isDirectory -> "Folder"
                                file.isLink -> "Link · ${file.size} bytes"
                                else -> "${file.size} bytes"
                            }
                            Text(detail, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (!state.busy && state.files.isEmpty() && state.error == null) {
                    item { Text("No files", modifier = Modifier.padding(16.dp)) }
                }
            }
        }
    }

    state.pendingUpload?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::cancelOverwrite,
            title = { Text("Replace ${pending.name}?") },
            text = { Text("A file with this name already exists on the remote system.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmOverwrite) { Text("Replace") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelOverwrite) { Text("Cancel") }
            }
        )
    }
}
