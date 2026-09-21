package uk.co.fireburn.lochssh.ui.hosts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import uk.co.fireburn.lochssh.data.db.PortForwardDao
import uk.co.fireburn.lochssh.data.db.SshHostDao
import uk.co.fireburn.lochssh.data.db.SshHostEntity
import uk.co.fireburn.lochssh.ssh.SessionRegistry
import javax.inject.Inject

@HiltViewModel
class HostsViewModel @Inject constructor(
    private val hostDao: SshHostDao,
    private val portForwardDao: PortForwardDao,
    private val registry: SessionRegistry
) : ViewModel() {

    val hosts: Flow<List<SshHostEntity>> = hostDao.observeAll()
    val sessions = registry.sessions

    fun newSessionId(): Long = registry.newId()

    fun dismissSession(id: Long) = registry.remove(id)

    fun duplicateHost(id: Long) {
        viewModelScope.launch {
            val original = hostDao.getById(id) ?: return@launch
            val newId = hostDao.insert(original.copy(id = 0, name = "${original.name} (copy)"))
            portForwardDao.getByHost(id).forEach {
                portForwardDao.insert(it.copy(id = 0, hostId = newId))
            }
        }
    }

    fun deleteHost(id: Long) {
        viewModelScope.launch {
            portForwardDao.deleteByHost(id)
            hostDao.getById(id)?.let { hostDao.delete(it) }
        }
    }
}
