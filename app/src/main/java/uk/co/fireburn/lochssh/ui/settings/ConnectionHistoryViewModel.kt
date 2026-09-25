package uk.co.fireburn.lochssh.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uk.co.fireburn.lochssh.data.ConnectionHistoryRecorder
import uk.co.fireburn.lochssh.data.db.ConnectionHistoryDao

@HiltViewModel
class ConnectionHistoryViewModel @Inject constructor(
    private val dao: ConnectionHistoryDao,
    private val recorder: ConnectionHistoryRecorder
) : ViewModel() {
    val entries = dao.observeRecent()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun clear() {
        viewModelScope.launch {
            runCatching { recorder.clear() }
                .onFailure { _error.value = "Could not clear connection history" }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
