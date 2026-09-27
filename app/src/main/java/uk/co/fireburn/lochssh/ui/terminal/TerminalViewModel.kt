package uk.co.fireburn.lochssh.ui.terminal

import androidx.lifecycle.ViewModel
import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import uk.co.fireburn.lochssh.data.AppPreferences
import uk.co.fireburn.lochssh.data.SavedCommandStore
import uk.co.fireburn.lochssh.data.db.SavedCommandDao
import uk.co.fireburn.lochssh.data.db.SavedCommandEntity
import uk.co.fireburn.lochssh.ssh.SessionRegistry
import uk.co.fireburn.lochssh.ssh.RemoteSessions
import uk.co.fireburn.lochssh.ssh.RemoteSessionEntry
import uk.co.fireburn.lochssh.ssh.RemoteSessionOptions
import javax.inject.Inject

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val registry: SessionRegistry,
    private val savedCommandDao: SavedCommandDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val fontSize = preferences.terminalFontSize
    val terminalTheme = preferences.terminalTheme
    val keyboardLayout = preferences.keyboardLayout
    private var pendingTranscript: String? = null
    val exportError = MutableStateFlow<String?>(null)

    fun prepareTranscript(buffer: TerminalBuffer) {
        pendingTranscript = buffer.transcript()
    }

    fun saveTranscript(uri: Uri?) {
        val text = pendingTranscript
        pendingTranscript = null
        if (uri == null) return
        if (text == null) {
            exportError.value = "Terminal text is no longer available. Try saving again."
            return
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val output = context.contentResolver.openOutputStream(uri, "wt")
                        ?: throw java.io.IOException("Could not open file")
                    output.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                exportError.value = "Could not save terminal text: ${error.message ?: "file error"}"
            }
        }
    }

    fun clearExportError() { exportError.value = null }
    val savedCommands = savedCommandDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val sessionId = MutableStateFlow(0L)

    val session = combine(registry.sessions, sessionId) { sessions, id ->
        sessions.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun load(id: Long) {
        sessionId.value = id
    }

    fun changeFontSize(step: Int) = preferences.changeTerminalFontSize(step)

    fun saveCommand(id: Long, name: String, command: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val error = withContext(Dispatchers.IO) { SavedCommandStore(savedCommandDao).save(id, name, command) }
            onDone(error)
        }
    }

    fun deleteCommand(command: SavedCommandEntity, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val error = withContext(Dispatchers.IO) { SavedCommandStore(savedCommandDao).delete(command) }
            onDone(error)
        }
    }

    fun dismissRemoteSessions() {
        registry.dismissRemoteSessions(sessionId.value)
    }

    fun dismissForwardWarnings() {
        registry.dismissForwardWarnings(sessionId.value)
    }

    fun checkRemoteSessions() {
        val id = sessionId.value
        val manager = registry.find(id)?.manager ?: return
        viewModelScope.launch {
            val options = withContext(Dispatchers.IO) {
                try {
                    manager.discoverRemoteSessions()
                } catch (_: Exception) {
                    RemoteSessionOptions(error = "Could not check remote sessions")
                }
            }
            if (registry.find(id)?.manager === manager) {
                registry.offerRemoteSessions(id, options)
            }
        }
    }

    fun attachRemoteSession(entry: RemoteSessionEntry) {
        sendRemoteCommand(RemoteSessions.attach(entry))
    }

    fun createRemoteSession(tool: String) {
        sendRemoteCommand(RemoteSessions.create(tool))
    }

    private fun sendRemoteCommand(command: String) {
        val id = sessionId.value
        registry.find(id)?.manager?.write((command + "\n").toByteArray(Charsets.UTF_8))
        registry.dismissRemoteSessions(id)
    }
}
