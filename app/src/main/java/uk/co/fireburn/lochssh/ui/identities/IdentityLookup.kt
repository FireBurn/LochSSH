package uk.co.fireburn.lochssh.ui.identities

import kotlinx.coroutines.CancellationException
import uk.co.fireburn.lochssh.data.db.IdentityEntity

internal data class IdentityLookupResult(val identity: IdentityEntity?, val error: String? = null)

internal object IdentityLookup {
    suspend fun load(id: Long, find: suspend (Long) -> IdentityEntity?): IdentityLookupResult {
        if (id == 0L) return IdentityLookupResult(null)
        if (id < 0) return IdentityLookupResult(null, "Invalid identity")
        return try {
            val identity = find(id)
            if (identity == null) IdentityLookupResult(null, "This identity no longer exists")
            else IdentityLookupResult(identity)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            IdentityLookupResult(null, "Could not load identity: ${e.message ?: "storage error"}")
        }
    }
}
