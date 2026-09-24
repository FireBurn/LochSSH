package uk.co.fireburn.lochssh.ui.identities

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.fireburn.lochssh.data.EncryptedStorageManager
import uk.co.fireburn.lochssh.data.db.AuthTypes
import uk.co.fireburn.lochssh.data.db.IdentityDao
import uk.co.fireburn.lochssh.data.db.IdentityEntity
import javax.inject.Inject

object KeySources {
    const val PATH = "PATH"
    const val PASTE = "PASTE"
}

@HiltViewModel
class IdentityEditorViewModel @Inject constructor(
    private val identityDao: IdentityDao,
    private val secrets: EncryptedStorageManager
) : ViewModel() {

    var identity by mutableStateOf<IdentityEntity?>(null)
        private set
    var hasKeyMaterial by mutableStateOf(false)
        private set
    var loaded by mutableStateOf(false)
        private set

    fun load(id: Long) {
        viewModelScope.launch {
            val e = if (id > 0) identityDao.getById(id) else null
            identity = e
            hasKeyMaterial = e?.keyMaterialRef != null
            loaded = true
        }
    }

    fun save(
        name: String,
        username: String,
        authType: String,
        keySource: String,
        keyPath: String,
        keyMaterial: String,
        secret: String
    ) = viewModelScope.launch {
        val existing = identity
        withContext(Dispatchers.IO + NonCancellable) {
            val created = mutableListOf<String>()
            fun saveNew(value: String): String {
                val ref = secrets.newReference()
                created += ref
                secrets.putSecret(ref, value)
                return ref
            }

            try {
                val secretRef = when {
                    authType == AuthTypes.NONE -> null
                    secret.isNotBlank() -> saveNew(secret)
                    existing?.authType == authType -> existing.secretRef
                    else -> null
                }
                val pasted = authType == AuthTypes.PUBLIC_KEY && keySource == KeySources.PASTE
                val keyMaterialRef = when {
                    !pasted -> null
                    keyMaterial.isNotBlank() -> saveNew(keyMaterial)
                    else -> existing?.keyMaterialRef
                }
                val keyPathFinal = if (authType == AuthTypes.PUBLIC_KEY && !pasted) {
                    keyPath.ifBlank { null }
                } else null

                val entity = existing?.copy(
                    name = name,
                    username = username,
                    authType = authType,
                    keyPath = keyPathFinal,
                    keyMaterialRef = keyMaterialRef,
                    secretRef = secretRef
                ) ?: IdentityEntity(
                    name = name,
                    username = username,
                    authType = authType,
                    keyPath = keyPathFinal,
                    keyMaterialRef = keyMaterialRef,
                    secretRef = secretRef
                )
                if (existing != null) identityDao.update(entity) else identityDao.insert(entity)

                listOfNotNull(existing?.secretRef, existing?.keyMaterialRef)
                    .filter { it != secretRef && it != keyMaterialRef }
                    .forEach { runCatching { secrets.deleteSecret(it) } }
            } catch (e: Exception) {
                created.forEach { runCatching { secrets.deleteSecret(it) } }
                throw e
            }
        }
    }
}
