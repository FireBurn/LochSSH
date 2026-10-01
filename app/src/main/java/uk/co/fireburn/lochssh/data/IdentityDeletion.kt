package uk.co.fireburn.lochssh.data

import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import uk.co.fireburn.lochssh.data.db.LochSshDatabase
import uk.co.fireburn.lochssh.data.db.SecretCleanupEntity
import javax.inject.Inject

class IdentityDeletion @Inject constructor(
    private val database: LochSshDatabase,
    private val secrets: EncryptedStorageManager
) {
    suspend fun delete(id: Long): String? = secrets.change {
        val references = try {
            database.withTransaction {
                val identities = database.identityDao()
                val identity = identities.getById(id)
                    ?: return@withTransaction null
                check(identities.deleteIfUnused(id) == 1) {
                    "Identity is used by a saved host"
                }
                val references = listOfNotNull(identity.secretRef, identity.keyMaterialRef).distinct()
                for (reference in references) {
                    database.secretCleanupDao().add(SecretCleanupEntity(reference))
                }
                references
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@change "Could not delete identity: ${e.message ?: "storage error"}"
        } ?: return@change "This identity no longer exists"

        val cleanup = database.secretCleanupDao()
        val remaining = SecretCleanup.retry(references, cleanup::isUsed,
            secrets::deleteSecret, cleanup::remove)
        if (remaining.isEmpty()) null
        else "Identity deleted, but could not remove all stored secrets"
    }
}
