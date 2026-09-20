package uk.co.fireburn.lochssh.ui.identities

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import uk.co.fireburn.lochssh.data.EncryptedStorageManager
import uk.co.fireburn.lochssh.data.db.IdentityDao
import uk.co.fireburn.lochssh.data.db.IdentityEntity
import javax.inject.Inject

@HiltViewModel
class IdentityEditorViewModel @Inject constructor(
    private val identityDao: IdentityDao,
    private val secrets: EncryptedStorageManager
) : ViewModel() {

    var identity by mutableStateOf<IdentityEntity?>(null)
        private set
    var loaded by mutableStateOf(false)
        private set

    fun load(id: Long) {
        viewModelScope.launch {
            identity = if (id > 0) identityDao.getById(id) else null
            loaded = true
        }
    }

    fun save(name: String, authType: String, keyPath: String, secret: String) {
        viewModelScope.launch {
            val existing = identity
            val ref = when {
                secret.isNotBlank() -> {
                    val r = existing?.secretRef ?: secrets.newReference()
                    secrets.putSecret(r, secret)
                    r
                }
                else -> existing?.secretRef
            }
            val entity = existing?.copy(
                name = name,
                authType = authType,
                keyPath = keyPath.ifBlank { null },
                secretRef = ref
            ) ?: IdentityEntity(
                name = name,
                authType = authType,
                keyPath = keyPath.ifBlank { null },
                secretRef = ref
            )
            if (existing != null) identityDao.update(entity) else identityDao.insert(entity)
        }
    }
}
