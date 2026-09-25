package uk.co.fireburn.lochssh.ui.sftp

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.fireburn.lochssh.ssh.RemoteFile
import uk.co.fireburn.lochssh.ssh.RemotePath
import uk.co.fireburn.lochssh.ssh.SessionRegistry
import java.io.IOException
import javax.inject.Inject

data class PendingUpload(val uri: Uri, val name: String)

data class SftpUiState(
    val path: String = ".",
    val files: List<RemoteFile> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val pendingUpload: PendingUpload? = null
)

@HiltViewModel
class SftpViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val registry: SessionRegistry
) : ViewModel() {
    private var sessionId = 0L
    private val _state = MutableStateFlow(SftpUiState())
    val state = _state.asStateFlow()

    fun load(id: Long) {
        if (sessionId == id) return
        sessionId = id
        browse(".")
    }

    fun browse(path: String) {
        val manager = registry.find(sessionId)?.manager
        if (manager == null) {
            _state.update { it.copy(error = "SSH session closed") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            runCatching { withContext(Dispatchers.IO) { manager.sftpBrowser().list(path) } }
                .onSuccess { directory ->
                    _state.update {
                        it.copy(path = directory.path, files = directory.files, busy = false)
                    }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(busy = false, error = failure.message ?: "Could not list files")
                    }
                }
        }
    }

    fun download(file: RemoteFile, uri: Uri) {
        val manager = registry.find(sessionId)?.manager ?: return
        val directory = state.value.path
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                        manager.sftpBrowser().download(directory, file.name, output)
                    } ?: throw IOException("Could not open the chosen file")
                }
            }.onSuccess {
                _state.update { it.copy(busy = false, message = "Downloaded ${file.name}") }
            }.onFailure { failure ->
                _state.update {
                    it.copy(busy = false,
                        error = "Download failed; the chosen file may be incomplete: " +
                            (failure.message ?: "transfer error"))
                }
            }
        }
    }

    fun prepareUpload(uri: Uri) {
        val manager = registry.find(sessionId)?.manager ?: return
        val directory = state.value.path
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val name = displayName(uri)
                    RemotePath.child(directory, name)
                    name to manager.sftpBrowser().exists(directory, name)
                }
            }.onSuccess { (name, exists) ->
                if (exists) {
                    _state.update {
                        it.copy(busy = false, pendingUpload = PendingUpload(uri, name))
                    }
                } else {
                    upload(uri, name, false)
                }
            }.onFailure { failure ->
                _state.update {
                    it.copy(busy = false, error = failure.message ?: "Could not check remote file")
                }
            }
        }
    }

    fun confirmOverwrite() {
        val pending = state.value.pendingUpload ?: return
        _state.update { it.copy(pendingUpload = null) }
        upload(pending.uri, pending.name, true)
    }

    fun cancelOverwrite() {
        _state.update { it.copy(pendingUpload = null) }
    }

    fun clearNotice() {
        _state.update { it.copy(error = null, message = null) }
    }

    private fun upload(uri: Uri, name: String, overwrite: Boolean) {
        val manager = registry.find(sessionId)?.manager ?: return
        val directory = state.value.path
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        manager.sftpBrowser().upload(directory, name, input, overwrite)
                    } ?: throw IOException("Could not open the chosen file")
                    manager.sftpBrowser().list(directory)
                }
            }.onSuccess { listing ->
                _state.update {
                    it.copy(path = listing.path, files = listing.files, busy = false,
                        message = "Uploaded $name")
                }
            }.onFailure { failure ->
                _state.update {
                    it.copy(busy = false, error = failure.message ?: "Upload failed")
                }
            }
        }
    }

    private fun displayName(uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: throw IOException("Could not read the chosen file name")
}
