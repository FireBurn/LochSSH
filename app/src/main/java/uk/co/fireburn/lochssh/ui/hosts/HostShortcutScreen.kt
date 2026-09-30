package uk.co.fireburn.lochssh.ui.hosts

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.CancellationException
import uk.co.fireburn.lochssh.data.db.SshHostEntity

@Composable
fun HostShortcutScreen(hostId: Long, onConnect: (Long, Long) -> Unit, onBack: () -> Unit,
    viewModel: HostsViewModel = hiltViewModel()) {
    var host by remember(hostId) { mutableStateOf<SshHostEntity?>(null) }
    var error by remember(hostId) { mutableStateOf<String?>(null) }
    var starting by remember(hostId) { mutableStateOf(false) }
    LaunchedEffect(hostId) {
        try {
            host = viewModel.hostById(hostId)
            if (host == null) error = "This saved host no longer exists"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Could not load host: ${e.message ?: "storage error"}"
        }
    }
    AlertDialog(onDismissRequest = onBack,
        title = { Text(host?.let { "Connect to ${it.name}?" } ?: "Saved host") },
        text = { Text(error ?: host?.let { "${it.host}:${it.port}" } ?: "Loading host") },
        confirmButton = {
            if (host != null && error == null) {
                TextButton(enabled = !starting, onClick = {
                    if (!starting) {
                        starting = true
                        onConnect(hostId, viewModel.newSessionId())
                    }
                }) { Text("Connect") }
            }
        },
        dismissButton = { TextButton(onClick = onBack) { Text("Close") } })
}
