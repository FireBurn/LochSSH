package uk.co.fireburn.lochssh.ui.hosts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import uk.co.fireburn.lochssh.data.db.ForwardTypes
import uk.co.fireburn.lochssh.data.db.IdentityDao
import uk.co.fireburn.lochssh.data.db.IdentityEntity
import uk.co.fireburn.lochssh.data.db.LochSshDatabase
import uk.co.fireburn.lochssh.data.db.PortForwardDao
import uk.co.fireburn.lochssh.data.db.PortForwardEntity
import uk.co.fireburn.lochssh.data.db.RemoteSessionModes
import uk.co.fireburn.lochssh.data.db.SshHostDao
import uk.co.fireburn.lochssh.data.db.SshHostEntity
import javax.inject.Inject
import uk.co.fireburn.lochssh.ssh.KeepAlive
import uk.co.fireburn.lochssh.ssh.JumpHostChain

@HiltViewModel
class HostEditorViewModel @Inject constructor(
    private val hostDao: SshHostDao,
    private val identityDao: IdentityDao,
    private val portForwardDao: PortForwardDao,
    private val database: LochSshDatabase
) : ViewModel() {

    val identities: Flow<List<IdentityEntity>> = identityDao.observeAll()
    val hosts = hostDao.observeAll()
    var host by mutableStateOf<SshHostEntity?>(null)
        private set
    var forwards by mutableStateOf<List<PortForwardEntity>>(emptyList())
        private set
    var loaded by mutableStateOf(false)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    private var loading: Job? = null

    fun load(id: Long) {
        loading?.cancel()
        loaded = false
        host = null
        forwards = emptyList()
        loadError = null
        loading = viewModelScope.launch {
            val result = HostLookup.load(id) { hostId ->
                database.withTransaction {
                    val saved = hostDao.getById(hostId) ?: return@withTransaction null
                    HostDetails(saved, portForwardDao.getByHost(hostId))
                }
            }
            coroutineContext.ensureActive()
            host = result.details?.host
            forwards = result.details?.forwards.orEmpty()
            loadError = result.error
            loaded = result.error == null
        }
    }

    fun addForward(type: String, localPort: String, remoteHost: String, remotePort: String): Boolean {
        if (!loaded) return false
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
        autoCommand: String,
        remoteSessionMode: String,
        jumpHostId: Long?,
        agentForwarding: Boolean,
        knockSequence: String = "",
        knockPause: String = "200",
        onFinished: (String?) -> Unit
    ) {
        viewModelScope.launch {
            val error = try {
                saveHost(name, hostName, port, keepAlive, group, identityId, autoCommand, remoteSessionMode, jumpHostId, agentForwarding, knockSequence, knockPause)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.message ?: "Could not save host"
            }
            onFinished(error)
        }
    }

    private suspend fun saveHost(
        name: String,
        hostName: String,
        port: String,
        keepAlive: String,
        group: String,
        identityId: Long?,
        autoCommand: String,
        remoteSessionMode: String,
        jumpHostId: Long?,
        agentForwarding: Boolean,
        knockSequence: String,
        knockPause: String
    ) {
        check(loaded) { loadError ?: "Wait for the host to load" }
        val existing = host
        require(name.isNotBlank()) { "Enter a name" }
        require(hostName.isNotBlank()) { "Enter a host" }
        val validPort = port.toIntOrNull()?.takeIf { it in 1..65535 }
            ?: throw IllegalArgumentException("Port must be between 1 and 65535")
        val validKeepAlive = KeepAlive.parse(keepAlive)
            ?: throw IllegalArgumentException("Keep-alive must be between 0 and ${KeepAlive.MAX_SECONDS} seconds")
        require(autoCommand.none { it == '\n' || it == '\r' }) { "Command must be one line" }
        require(remoteSessionMode in RemoteSessionModes.ALL) { "Choose a remote session option" }
        uk.co.fireburn.lochssh.ssh.KnockSequence.parse(knockSequence)
        val pause = if (knockSequence.isBlank()) 200L else knockPause.toLongOrNull()
        require(pause != null && pause in 0..5000) { "Pause must be between 0 and 5000 milliseconds" }
        require(jumpHostId == null || knockSequence.isBlank()) { "Configure knocking on the first jump host instead" }
        val entity = existing?.copy(
            name = name,
            host = hostName,
            port = validPort,
            keepAliveSeconds = validKeepAlive,
            group = group,
            identityId = identityId,
            autoCommand = autoCommand,
            remoteSessionMode = remoteSessionMode,
            jumpHostId = jumpHostId,
            agentForwarding = agentForwarding,
            knockSequence = knockSequence.trim(),
            knockPause = pause
        ) ?: SshHostEntity(
            name = name,
            host = hostName,
            port = validPort,
            keepAliveSeconds = validKeepAlive,
            group = group,
            identityId = identityId,
            autoCommand = autoCommand,
            remoteSessionMode = remoteSessionMode,
            jumpHostId = jumpHostId,
            agentForwarding = agentForwarding,
            knockSequence = knockSequence.trim(),
            knockPause = pause
        )
        database.withTransaction {
            HostIdentity.check(identityId, agentForwarding, identityDao::getById)
            JumpHostChain.resolve(entity, hostDao::getById)
            val hostId = if (existing != null) {
                check(hostDao.update(entity) == 1) { "This host no longer exists" }
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
