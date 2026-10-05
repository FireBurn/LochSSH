package uk.co.fireburn.lochssh.ui.terminal

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.fireburn.lochssh.ssh.RecordingCoordinator

@Composable
internal fun RecordingStatus(recording: RecordingCoordinator.Recording?, starting: Boolean, error: String?) {
    if (starting) Text("Starting recording")
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (recording == null) return
    val state by recording.writer.state.collectAsStateWithLifecycle()
    val completion by recording.completion.collectAsStateWithLifecycle()
    val failure = completion?.error ?: state.error
    val text = when {
        failure != null -> failure
        completion != null -> "Recording saved in private app storage"
        state.accepting -> "Recording"
        else -> "Stopping recording"
    }
    Text(text, color = if (failure != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
}
