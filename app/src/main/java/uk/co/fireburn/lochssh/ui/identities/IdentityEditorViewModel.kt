package uk.co.fireburn.lochssh.ui.identities

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
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

        val secretRef = when {
            secret.isNotBlank() -> {
                val r = existing?.secretRef ?: secrets.newReference()
                secrets.putSecret(r, secret)
                r
            }
            else -> existing?.secretRef
        }

        var keyPathFinal: String? = null
        var keyMaterialRefFinal: String? = null
        if (authType == AuthTypes.PUBLIC_KEY && keySource == KeySources.PASTE) {
            keyMaterialRefFinal = when {
                keyMaterial.isNotBlank() -> {
                    val r = existing?.keyMaterialRef ?: secrets.newReference()
                    secrets.putSecret(r, keyMaterial)
                    r
                }
                else -> existing?.keyMaterialRef
            }
            existing?.keyMaterialRef?.let { if (it != keyMaterialRefFinal) secrets.deleteSecret(it) }
        } else if (authType == AuthTypes.PUBLIC_KEY) {
            keyPathFinal = keyPath.ifBlank { null }
            existing?.keyMaterialRef?.let { secrets.deleteSecret(it) }
        }

        val entity = existing?.copy(
            name = name,
            username = username,
            authType = authType,
            keyPath = keyPathFinal,
            keyMaterialRef = keyMaterialRefFinal,
            secretRef = secretRef
        ) ?: IdentityEntity(
            name = name,
            username = username,
            authType = authType,
            keyPath = keyPathFinal,
            keyMaterialRef = keyMaterialRefFinal,
            secretRef = secretRef
        )
        if (existing != null) identityDao.update(entity) else identityDao.insert(entity)
    }
}
