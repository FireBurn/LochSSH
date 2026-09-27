package uk.co.fireburn.lochssh.ui.hosts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uk.co.fireburn.lochssh.data.db.PortForwardDao
import uk.co.fireburn.lochssh.data.db.LochSshDatabase
import uk.co.fireburn.lochssh.data.db.SshHostDao
import uk.co.fireburn.lochssh.data.db.SshHostEntity
import uk.co.fireburn.lochssh.ssh.SessionRegistry
import javax.inject.Inject

@HiltViewModel
class HostsViewModel @Inject constructor(
    private val hostDao: SshHostDao,
    private val portForwardDao: PortForwardDao,
    private val registry: SessionRegistry,
    private val database: LochSshDatabase
) : ViewModel() {

    val hosts: Flow<List<SshHostEntity>> = hostDao.observeAll()
    val sessions = registry.sessions
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    fun newSessionId(): Long = registry.newId()

    fun dismissSession(id: Long) = registry.remove(id)

    fun duplicateHost(id: Long) {
        viewModelScope.launch {
            runCatching {
                database.withTransaction {
                    val original = hostDao.getById(id)
                        ?: throw IllegalStateException("Host no longer exists")
                    val newId = hostDao.insert(original.copy(id = 0, name = "${original.name} (copy)"))
                    portForwardDao.getByHost(id).forEach {
                        portForwardDao.insert(it.copy(id = 0, hostId = newId))
                    }
                }
            }.onFailure { _error.value = "Could not duplicate host: ${it.message ?: "storage error"}" }
        }
    }

    fun deleteHost(id: Long) {
        viewModelScope.launch {
            runCatching {
                database.withTransaction {
                    val host = hostDao.getById(id)
                        ?: throw IllegalStateException("Host no longer exists")
                    require(hostDao.countUsingJump(id) == 0) { "This host is used as a jump host. Change those hosts first." }
                    portForwardDao.deleteByHost(id)
                    hostDao.delete(host)
                }
            }.onFailure { _error.value = "Could not delete host: ${it.message ?: "storage error"}" }
        }
    }
}
