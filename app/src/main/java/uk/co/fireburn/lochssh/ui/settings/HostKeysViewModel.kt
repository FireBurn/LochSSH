package uk.co.fireburn.lochssh.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.fireburn.lochssh.ssh.HostKeyTrust
import uk.co.fireburn.lochssh.ssh.SavedHostKey
import javax.inject.Inject

@HiltViewModel
class HostKeysViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _keys = MutableStateFlow<List<SavedHostKey>>(emptyList())
    val keys = _keys.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { HostKeyTrust.saved(context) } }
                .onSuccess { _keys.value = it; _error.value = null }
                .onFailure { _error.value = it.message ?: "Could not load host keys" }
        }
    }

    fun remove(key: SavedHostKey) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    HostKeyTrust.remove(context, key)
                    HostKeyTrust.saved(context)
                }
            }.onSuccess { _keys.value = it; _error.value = null }
                .onFailure { _error.value = it.message ?: "Could not remove host key" }
        }
    }
}
