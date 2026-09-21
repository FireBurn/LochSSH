package uk.co.fireburn.lochssh.ui.hosts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import uk.co.fireburn.lochssh.data.db.IdentityDao
import uk.co.fireburn.lochssh.data.db.IdentityEntity
import uk.co.fireburn.lochssh.data.db.PortForwardDao
import uk.co.fireburn.lochssh.data.db.PortForwardEntity
import uk.co.fireburn.lochssh.data.db.SshHostDao
import uk.co.fireburn.lochssh.data.db.SshHostEntity
import javax.inject.Inject

@HiltViewModel
class HostEditorViewModel @Inject constructor(
    private val hostDao: SshHostDao,
    private val identityDao: IdentityDao,
    private val portForwardDao: PortForwardDao
) : ViewModel() {

    val identities: Flow<List<IdentityEntity>> = identityDao.observeAll()
    var host by mutableStateOf<SshHostEntity?>(null)
        private set
    var forwards by mutableStateOf<List<PortForwardEntity>>(emptyList())
        private set
    var loaded by mutableStateOf(false)
        private set

    fun load(id: Long) {
        viewModelScope.launch {
            host = if (id > 0) hostDao.getById(id) else null
            forwards = if (id > 0) portForwardDao.getByHost(id) else emptyList()
            loaded = true
        }
    }

    fun addForward(type: String, localPort: String, remoteHost: String, remotePort: String) {
        val lp = localPort.toIntOrNull() ?: return
        val rp = remotePort.toIntOrNull() ?: return
        if (remoteHost.isBlank()) return
        forwards = forwards + PortForwardEntity(
            hostId = 0,
            type = type,
            localPort = lp,
            remoteHost = remoteHost,
            remotePort = rp
        )
    }

    fun removeForward(index: Int) {
        forwards = forwards.filterIndexed { i, _ -> i != index }
    }

    fun save(
        name: String,
        hostName: String,
        port: String,
        keepAlive: String,
        group: String,
        identityId: Long?
    ) = viewModelScope.launch {
        val existing = host
        val entity = existing?.copy(
            name = name,
            host = hostName,
            port = port.toIntOrNull() ?: 22,
            keepAliveSeconds = keepAlive.toIntOrNull() ?: 30,
            group = group,
            identityId = identityId
        ) ?: SshHostEntity(
            name = name,
            host = hostName,
            port = port.toIntOrNull() ?: 22,
            keepAliveSeconds = keepAlive.toIntOrNull() ?: 30,
            group = group,
            identityId = identityId
        )
        val hostId = if (existing != null) {
            hostDao.update(entity)
            existing.id
        } else {
            hostDao.insert(entity)
        }
        portForwardDao.deleteByHost(hostId)
        forwards.forEach { f ->
            portForwardDao.insert(f.copy(id = 0, hostId = hostId))
        }
    }
}
