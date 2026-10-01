package uk.co.fireburn.lochssh.ui.identities

import kotlinx.coroutines.CancellationException
import uk.co.fireburn.lochssh.data.SecretRemoval
import uk.co.fireburn.lochssh.data.db.IdentityEntity

internal object IdentityRemoval {
    suspend fun remove(
        identity: IdentityEntity,
        deleteRow: suspend (Long) -> Int,
        deleteSecret: (String) -> Unit
    ): String? {
        try {
            if (deleteRow(identity.id) == 0) {
                return "Identity is used by a saved host or was already removed"
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return "Could not delete identity: ${e.message ?: "storage error"}"
        }
        val remaining = SecretRemoval.remove(listOfNotNull(identity.secretRef, identity.keyMaterialRef), deleteSecret)
        return if (remaining.isNotEmpty()) "Identity deleted, but could not remove all stored secrets" else null
    }
}
