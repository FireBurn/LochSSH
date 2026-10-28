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
import uk.co.fireburn.lochssh.ssh.RemoteSessionOptions
import uk.co.fireburn.lochssh.ssh.HostKeyTrust
import uk.co.fireburn.lochssh.ssh.SavedHostKey
import javax.inject.Inject

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val registry: SessionRegistry,
    private val savedCommandDao: SavedCommandDao,
    @ApplicationContext private val context: Context,
    internal val recordings: uk.co.fireburn.lochssh.data.SessionRecordings,
    private val memory: uk.co.fireburn.lochssh.ssh.SessionMemory
) : ViewModel() {

    val fontSize = preferences.terminalFontSize
    val sessionShortcuts = preferences.sessionShortcuts
    val terminalTypeface = preferences.terminalTypeface
    val wrapLines = preferences.wrapLines
    val terminalTheme = preferences.terminalTheme
    val keyboardLayout = preferences.keyboardLayout
    val sessions = registry.sessions
    val replacements = registry.replacements
    private var pendingTranscript: String? = null
    val exportError = MutableStateFlow<String?>(null)
    private var pendingRecording: uk.co.fireburn.lochssh.ssh.RecordingFiles.Entry? = null
    val recordingExportError = MutableStateFlow<String?>(null)

    internal fun prepareRecording(entry: uk.co.fireburn.lochssh.ssh.RecordingFiles.Entry) {
        pendingRecording = entry
    }

    fun saveRecording(uri: Uri?) {
        val entry = pendingRecording
        pendingRecording = null
        if (uri == null) return
        if (entry == null) {
            recordingExportError.value = "Recording is no longer selected. Try exporting again."
            return
        }
        viewModelScope.launch {
            try { recordings.export(entry, uri) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { recordingExportError.value = "Could not export recording. Check that it is still available and try another file." }
        }
    }

    fun clearRecordingExportError() { recordingExportError.value = null }

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

    fun remembered(id: Long) = memory.find(id)

    fun leftTerminal() = memory.view(null)

    fun load(id: Long) {
        sessionId.value = id
        memory.view(id)
    }

    fun newSessionId(): Long = registry.newId()

    fun changeFontSize(step: Int) = preferences.changeTerminalFontSize(step)

    fun dismissHostKeyReview() = registry.dismissHostKeyReview(sessionId.value)

    fun acceptHostKey(key: SavedHostKey, onDone: (String?) -> Unit) {
        val id = sessionId.value
        viewModelScope.launch {
            val error = try {
                check(registry.find(id)?.hostKeyReview == key) { "Server key review has changed" }
                withContext(Dispatchers.IO) { HostKeyTrust.accept(context, key) }
                registry.dismissHostKeyReview(id)
                null
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { failure.message ?: "Could not save server key" }
            onDone(error)
        }
    }

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

}
