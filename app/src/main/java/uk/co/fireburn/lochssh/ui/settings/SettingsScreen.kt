package uk.co.fireburn.lochssh.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.fireburn.lochssh.ui.keyboard.KeyboardLayoutEditor
import uk.co.fireburn.lochssh.ui.terminal.TerminalTheme
import androidx.compose.material3.RadioButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onHostKeys: () -> Unit,
    onHistory: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    var confirmClear by remember { mutableStateOf(false) }
    var editKeyboard by remember { mutableStateOf(false) }
    val keyboardLayout by viewModel.keyboardLayout.collectAsStateWithLifecycle()
    val fontSize by viewModel.fontSize.collectAsStateWithLifecycle()
    val terminalTheme by viewModel.terminalTheme.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
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
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("LochSSH", style = MaterialTheme.typography.titleMedium)
                    Text("uk.co.fireburn.lochssh", style = MaterialTheme.typography.bodySmall)
                    Text("License: BSD 3-Clause", style = MaterialTheme.typography.bodySmall)
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Terminal font size", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${fontSize}sp, also on the terminal bar",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    TextButton(onClick = { viewModel.changeFontSize(-1) }) { Text("Aa-") }
                    TextButton(onClick = { viewModel.changeFontSize(1) }) { Text("Aa+") }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Terminal theme", style = MaterialTheme.typography.titleMedium)
                    TerminalTheme.entries.forEach { theme ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = terminalTheme == theme,
                                onClick = { viewModel.setTerminalTheme(theme) })
                            TextButton(onClick = { viewModel.setTerminalTheme(theme) }) { Text(theme.label) }
                        }
                    }
                }
            }
            Card(onClick = { editKeyboard = true }, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Terminal keyboard", style = MaterialTheme.typography.titleMedium)
                    Text("Choose keys, change their order and add rows")
                }
            }
            BatterySettingsCard()
            Card(onClick = onHostKeys, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Saved host keys", style = MaterialTheme.typography.titleMedium)
                    Text("Review fingerprints or remove a key after a server change")
                }
            }
            Card(onClick = onHistory, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Connection history", style = MaterialTheme.typography.titleMedium)
                    Text("Recent connections and their outcomes")
                }
            }
            Card(
                onClick = { confirmClear = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Clear all data",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (editKeyboard) {
        KeyboardLayoutEditor(keyboardLayout, onSave = {
            viewModel.setKeyboardLayout(it)
            editKeyboard = false
        }, onDismiss = { editKeyboard = false })
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all data?") },
            text = { Text("Remove hosts, identities, saved commands, history, secrets, host keys and settings? Open sessions stay connected and may make new history entries.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAll()
                    confirmClear = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            }
        )
    }
    error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("Could not clear data") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::clearError) { Text("OK") }
            }
        )
    }
}
