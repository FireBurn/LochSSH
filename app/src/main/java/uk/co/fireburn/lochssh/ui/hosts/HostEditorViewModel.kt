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
import uk.co.fireburn.lochssh.data.db.SshHostDao
import uk.co.fireburn.lochssh.data.db.SshHostEntity
import javax.inject.Inject

@HiltViewModel
class HostEditorViewModel @Inject constructor(
    private val hostDao: SshHostDao,
    private val identityDao: IdentityDao
) : ViewModel() {

    val identities: Flow<List<IdentityEntity>> = identityDao.observeAll()
    var host by mutableStateOf<SshHostEntity?>(null)
        private set
    var loaded by mutableStateOf(false)
        private set

    fun load(id: Long) {
        viewModelScope.launch {
            host = if (id > 0) hostDao.getById(id) else null
            loaded = true
        }
    }

    fun save(
        name: String,
        hostName: String,
        port: String,
        keepAlive: String,
        group: String,
        identityId: Long?
    ) {
        viewModelScope.launch {
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
            if (existing != null) hostDao.update(entity) else hostDao.insert(entity)
        }
    }
}
