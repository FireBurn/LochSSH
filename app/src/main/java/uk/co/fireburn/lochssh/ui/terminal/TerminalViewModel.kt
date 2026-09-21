package uk.co.fireburn.lochssh.ui.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import uk.co.fireburn.lochssh.data.AppPreferences
import uk.co.fireburn.lochssh.ssh.SessionRegistry
import javax.inject.Inject

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val preferences: AppPreferences,
    registry: SessionRegistry
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
}
