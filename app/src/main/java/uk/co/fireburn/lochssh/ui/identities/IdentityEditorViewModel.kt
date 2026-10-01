package uk.co.fireburn.lochssh.ui.identities

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.fireburn.lochssh.data.EncryptedStorageManager
import uk.co.fireburn.lochssh.data.SecretCleanup
import java.io.IOException
import uk.co.fireburn.lochssh.data.db.AuthTypes
import uk.co.fireburn.lochssh.data.db.IdentityDao
import uk.co.fireburn.lochssh.data.db.IdentityEntity
import uk.co.fireburn.lochssh.data.db.LochSshDatabase
import uk.co.fireburn.lochssh.data.db.SecretCleanupEntity
import javax.inject.Inject

object KeySources {
    const val PATH = "PATH"
    const val PASTE = "PASTE"
}

@HiltViewModel
class IdentityEditorViewModel @Inject constructor(
    private val identityDao: IdentityDao,
    private val secrets: EncryptedStorageManager,
    private val database: LochSshDatabase
) : ViewModel() {

    var identity by mutableStateOf<IdentityEntity?>(null)
        private set
    var hasKeyMaterial by mutableStateOf(false)
        private set
    var loaded by mutableStateOf(false)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    private var loading: Job? = null

    fun load(id: Long) {
        loading?.cancel()
        loaded = false
        identity = null
        hasKeyMaterial = false
        loadError = null
        loading = viewModelScope.launch {
            val result = IdentityLookup.load(id, identityDao::getById)
            coroutineContext.ensureActive()
            identity = result.identity
            hasKeyMaterial = result.identity?.keyMaterialRef != null
            loadError = result.error
            loaded = result.error == null
        }
    }

    fun save(
        name: String,
        username: String,
        authType: String,
        keySource: String,
        keyPath: String,
        keyMaterial: String,
        secret: String,
        onFinished: (String?) -> Unit
    ) {
        viewModelScope.launch {
            if (!loaded) {
                onFinished(loadError ?: "Wait for the identity to load")
                return@launch
            }
            val existing = identity
            val inputError = IdentityInput.error(
                name, username, authType, keySource, keyPath, keyMaterial, secret, existing
            )
            if (inputError != null) {
                onFinished(inputError)
                return@launch
            }
            try {
                val saved = withContext(Dispatchers.IO + NonCancellable) {
                    secrets.change {
                        val created = mutableListOf<String>()
                        val cleanup = database.secretCleanupDao()
                        var rowSaved = false
                        suspend fun saveNew(value: String): String {
                            val ref = secrets.newReference()
                            cleanup.add(SecretCleanupEntity(ref))
                            created += ref
                            secrets.putSecret(ref, value)
                            return ref
                        }

                        try {
                            val current = if (existing == null) null else {
                                identityDao.getById(existing.id)
                                    ?: error("This identity no longer exists")
                            }
                            val currentError = IdentityInput.error(name, username, authType,
                                keySource, keyPath, keyMaterial, secret, current)
                            check(currentError == null) { requireNotNull(currentError) }
                            val secretRef = when {
                                authType == AuthTypes.NONE -> null
                                secret.isNotBlank() -> saveNew(secret)
                                current?.authType == authType -> current.secretRef
                                else -> null
                            }
                            val pasted = authType == AuthTypes.PUBLIC_KEY && keySource == KeySources.PASTE
                            val keyMaterialRef = when {
                                !pasted -> null
                                keyMaterial.isNotBlank() -> saveNew(keyMaterial)
                                else -> current?.keyMaterialRef
                            }
                            val keyPathFinal = if (authType == AuthTypes.PUBLIC_KEY && !pasted) {
                                keyPath.ifBlank { null }
                            } else null

                            val entity = current?.copy(
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
                            val oldReferences = listOfNotNull(current?.secretRef, current?.keyMaterialRef)
                                .filter { it != secretRef && it != keyMaterialRef }.distinct()
                            val savedEntity = database.withTransaction {
                                val saved = if (current != null) {
                                    check(identityDao.update(entity) == 1) { "This identity no longer exists" }
                                    entity
                                } else {
                                    entity.copy(id = identityDao.insert(entity))
                                }
                                for (reference in oldReferences) cleanup.add(SecretCleanupEntity(reference))
                                for (reference in created) cleanup.remove(reference)
                                saved
                            }
                            rowSaved = true

                            val remaining = SecretCleanup.retry(oldReferences, cleanup::isUsed,
                                secrets::deleteSecret, cleanup::remove)
                            savedEntity to remaining.isNotEmpty()
                        } catch (e: Exception) {
                            if (rowSaved) throw e
                            val remaining = SecretCleanup.retry(created, cleanup::isUsed,
                                secrets::deleteSecret, cleanup::remove)
                            if (remaining.isNotEmpty() && e !is CancellationException) {
                                throw IOException("${e.message ?: "Could not save identity"}; could not remove new stored secrets", e)
                            }
                            throw e
                        }
                    }
                }
                identity = saved.first
                hasKeyMaterial = saved.first.keyMaterialRef != null
                onFinished(if (saved.second) "Identity saved, but could not remove old stored secrets" else null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onFinished(e.message ?: "Could not save identity")
            }
        }
    }
}
