package uk.co.fireburn.lochssh.ui.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import uk.co.fireburn.lochssh.data.db.SshHostDao
import javax.inject.Inject

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val hostDao: SshHostDao
) : ViewModel() {

    var hostName: String = ""
        private set

    fun load(hostId: Long) {
        viewModelScope.launch {
            hostName = hostDao.getById(hostId)?.name ?: ""
        }
    }
}
