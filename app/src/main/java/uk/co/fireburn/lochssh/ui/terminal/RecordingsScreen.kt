package uk.co.fireburn.lochssh.ui.terminal

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun RecordingsScreen(onBack: () -> Unit, viewModel: RecordingsViewModel = hiltViewModel()) {
    val error by viewModel.error.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {
        viewModel.export(it)
    }
    RecordingsDialog(viewModel.store, onExport = {
        viewModel.select(it)
        picker.launch("lochssh-${it.id}.terminal")
    }, onDismiss = onBack)
    error?.let { message ->
        AlertDialog(onDismissRequest = viewModel::clearError,
            title = { Text("Could not export recording") }, text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text("OK") } })
    }
}
