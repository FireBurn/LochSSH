package uk.co.fireburn.lochssh.ui.hosts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import uk.co.fireburn.lochssh.data.db.ForwardTypes
import uk.co.fireburn.lochssh.data.db.IdentityDao
import uk.co.fireburn.lochssh.data.db.IdentityEntity
import uk.co.fireburn.lochssh.data.db.LochSshDatabase
import uk.co.fireburn.lochssh.data.db.PortForwardDao
import uk.co.fireburn.lochssh.data.db.PortForwardEntity
import uk.co.fireburn.lochssh.data.db.SshHostDao
import uk.co.fireburn.lochssh.data.db.SshHostEntity
import javax.inject.Inject

@HiltViewModel
class HostEditorViewModel @Inject constructor(
    private val hostDao: SshHostDao,
    private val identityDao: IdentityDao,
    private val portForwardDao: PortForwardDao,
    private val database: LochSshDatabase
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

    fun addForward(type: String, localPort: String, remoteHost: String, remotePort: String): Boolean {
        val lp = localPort.toIntOrNull() ?: return false
        val rp = if (type == ForwardTypes.DYNAMIC) 0 else remotePort.toIntOrNull() ?: return false
        val minLocalPort = if (type == ForwardTypes.REMOTE) 1 else 1024
        if (lp !in minLocalPort..65535 ||
            (type != ForwardTypes.DYNAMIC && rp !in 1..65535) ||
            (type == ForwardTypes.LOCAL && remoteHost.isBlank()) ||
            type !in listOf(ForwardTypes.LOCAL, ForwardTypes.REMOTE, ForwardTypes.DYNAMIC)
        ) return false
        forwards = forwards + PortForwardEntity(
            hostId = 0,
            type = type,
            localPort = lp,
            remoteHost = if (type == ForwardTypes.LOCAL) remoteHost else "",
            remotePort = rp
        )
        return true
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
        identityId: Long?,
        autoCommand: String
    ) = viewModelScope.launch {
        val existing = host
        val validPort = port.toIntOrNull()?.takeIf { it in 1..65535 }
            ?: throw IllegalArgumentException("Port must be between 1 and 65535")
        val validKeepAlive = keepAlive.toIntOrNull()?.takeIf { it >= 0 }
            ?: throw IllegalArgumentException("Keep-alive must not be negative")
        require(autoCommand.none { it == '\n' || it == '\r' }) { "Command must be one line" }
        val entity = existing?.copy(
            name = name,
            host = hostName,
            port = validPort,
            keepAliveSeconds = validKeepAlive,
            group = group,
            identityId = identityId,
            autoCommand = autoCommand
        ) ?: SshHostEntity(
            name = name,
            host = hostName,
            port = validPort,
            keepAliveSeconds = validKeepAlive,
            group = group,
            identityId = identityId,
            autoCommand = autoCommand
        )
        database.withTransaction {
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
}
