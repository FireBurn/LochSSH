package uk.co.fireburn.lochssh.ui.terminal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import uk.co.fireburn.lochssh.data.SessionRecordings
import uk.co.fireburn.lochssh.ssh.RecordingFiles

@Composable
internal fun RecordingsDialog(store: SessionRecordings, onExport: (RecordingFiles.Entry) -> Unit, onDismiss: () -> Unit) {
    val active by store.recordings.collectAsStateWithLifecycle()
    var entries by remember { mutableStateOf<List<RecordingFiles.Entry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var deleting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<RecordingFiles.Entry?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(active) {
        try { entries = store.list() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "Could not read recordings" }
        finally { loading = false }
    }
    selected?.let { entry ->
        AlertDialog(onDismissRequest = { if (!deleting) selected = null },
            title = { Text("Delete recording?") },
            text = { Text("Delete the saved output from ${entry.hostLabel}? This cannot be undone.") },
            confirmButton = { TextButton(modifier = Modifier.testTag("confirm-recording-delete"), enabled = !deleting, onClick = {
                deleting = true
                scope.launch {
                    try { store.delete(entry); entries = store.list(); selected = null }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { error = "Could not delete recording"; selected = null }
                    finally { deleting = false }
                }
            }) { Text("Delete") } },
            dismissButton = { TextButton(enabled = !deleting, onClick = { selected = null }) { Text("Cancel") } })
    }
    AlertDialog(onDismissRequest = { if (!deleting) onDismiss() },
        title = { Text("Recordings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Raw terminal output, including escape sequences. Files may contain secrets; exports are not plain text or replays.")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (loading) Text("Loading recordings")
                else if (entries.isEmpty()) Text("No recordings")
                entries.forEach { entry ->
                    val busy = active.values.any { it.id == entry.id }
                    Text(entry.hostLabel)
                    Text(if (entry.startedAt > 0) DateFormat.getDateTimeInstance().format(Date(entry.startedAt)) else "Start time unavailable")
                    Text("${entry.size} bytes · ${if (busy) "Recording or closing" else if (entry.complete) "Complete" else "Incomplete"}")
                    Row {
                        TextButton(enabled = !busy && !deleting, onClick = { onExport(entry) }) { Text("Export") }
                        TextButton(enabled = !busy && !deleting, onClick = { selected = entry }) { Text("Delete") }
                    }
                }
            }
        }, confirmButton = { TextButton(enabled = !deleting, onClick = onDismiss) { Text("Close") } })
}
