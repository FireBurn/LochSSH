package uk.co.fireburn.lochssh.ui.hosts

import kotlinx.coroutines.CancellationException
import uk.co.fireburn.lochssh.data.db.PortForwardEntity
import uk.co.fireburn.lochssh.data.db.SshHostEntity

internal data class HostDetails(val host: SshHostEntity, val forwards: List<PortForwardEntity>)
internal data class HostLookupResult(val details: HostDetails?, val error: String? = null)

internal object HostLookup {
    suspend fun load(id: Long, read: suspend (Long) -> HostDetails?): HostLookupResult {
        if (id == 0L) return HostLookupResult(null)
        if (id < 0) return HostLookupResult(null, "Invalid host")
        return try {
            val details = read(id)
            if (details == null) HostLookupResult(null, "This host no longer exists")
            else HostLookupResult(details)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            HostLookupResult(null, "Could not load host: ${e.message ?: "storage error"}")
        }
    }
}
