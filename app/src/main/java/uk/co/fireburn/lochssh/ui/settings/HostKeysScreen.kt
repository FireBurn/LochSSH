package uk.co.fireburn.lochssh.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.fireburn.lochssh.ssh.SavedHostKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostKeysScreen(
    onBack: () -> Unit,
    viewModel: HostKeysViewModel = hiltViewModel()
) {
    val keys by viewModel.keys.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<SavedHostKey?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved host keys") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (error != null) {
                item { Text(error.orEmpty(), color = MaterialTheme.colorScheme.error) }
            }
            if (keys.isEmpty()) {
                item { Text("No saved host keys") }
            }
            items(keys, key = { "${it.host}:${it.type}:${it.key}" }) { key ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(key.host, style = MaterialTheme.typography.titleMedium)
                        Text(key.type, style = MaterialTheme.typography.bodySmall)
                        SelectionContainer {
                            Text(key.fingerprint, style = MaterialTheme.typography.bodyMedium)
                        }
                        TextButton(onClick = { selected = key }) { Text("Remove key") }
                    }
                }
            }
        }
    }

    selected?.let { key ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("Remove saved key?") },
            text = {
                Text("${key.host} will be treated as a new server on the next connection. " +
                    "Only remove this key if you have confirmed the server changed.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.remove(key)
                    selected = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { selected = null }) { Text("Cancel") }
            }
        )
    }
}
