package uk.co.fireburn.lochssh.ui.terminal

import androidx.lifecycle.ViewModel
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
import uk.co.fireburn.lochssh.ssh.SessionRegistry
import uk.co.fireburn.lochssh.ssh.RemoteSessions
import uk.co.fireburn.lochssh.ssh.RemoteSessionEntry
import uk.co.fireburn.lochssh.ssh.RemoteSessionOptions
import javax.inject.Inject

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val registry: SessionRegistry
) : ViewModel() {

    val fontSize = preferences.terminalFontSize

    private val sessionId = MutableStateFlow(0L)

    val session = combine(registry.sessions, sessionId) { sessions, id ->
        sessions.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun load(id: Long) {
        sessionId.value = id
    }

    fun changeFontSize(step: Int) = preferences.changeTerminalFontSize(step)

    fun dismissRemoteSessions() {
        registry.dismissRemoteSessions(sessionId.value)
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
