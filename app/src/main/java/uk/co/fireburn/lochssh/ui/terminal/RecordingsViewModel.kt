package uk.co.fireburn.lochssh.ui.terminal

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import uk.co.fireburn.lochssh.data.SessionRecordings
import uk.co.fireburn.lochssh.ssh.RecordingFiles

@HiltViewModel
class RecordingsViewModel @Inject constructor(internal val store: SessionRecordings) : ViewModel() {
    private var pending: RecordingFiles.Entry? = null
    val error = MutableStateFlow<String?>(null)

    internal fun select(entry: RecordingFiles.Entry) { pending = entry }

    fun export(destination: Uri?) {
        val entry = pending
        pending = null
        if (destination == null) return
        if (entry == null) {
            error.value = "Recording is no longer selected. Try exporting again."
            return
        }
        viewModelScope.launch {
            try { store.export(entry, destination) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error.value = "Could not export recording. Check that it is still available and try another file." }
        }
    }

    fun clearError() { error.value = null }
}
