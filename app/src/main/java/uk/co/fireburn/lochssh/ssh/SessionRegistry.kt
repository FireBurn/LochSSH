package uk.co.fireburn.lochssh.ssh

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

// Every open connection, so the UI can resume one instead of starting another.
@Singleton
class SessionRegistry @Inject constructor() {

    data class Session(
        val id: Long,
        val hostId: Long,
        val hostName: String,
        val manager: SshConnectionManager? = null,
        val error: String? = null,
        val title: String? = null,
        val remoteOptions: RemoteSessionOptions? = null
    ) {
        // What a terminal would put on the tab, falling back to the host.
        val label: String get() = title?.takeIf { it.isNotBlank() } ?: hostName

        val isConnected: Boolean get() = manager?.isConnected == true
    }

    private val nextId = AtomicLong(1)
    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions

    fun newId(): Long = nextId.getAndIncrement()

    fun opening(id: Long, hostId: Long, hostName: String) = update(id) {
        Session(id = id, hostId = hostId, hostName = hostName)
    }

    fun connected(id: Long, manager: SshConnectionManager) = update(id) {
        it?.copy(manager = manager, error = null)
    }

    fun titled(id: Long, title: String) = update(id) {
        if (it?.title == title) null else it?.copy(title = title)
    }

    fun failed(id: Long, message: String) = update(id) {
        it?.copy(manager = null, error = message)
    }

    fun offerRemoteSessions(id: Long, options: RemoteSessionOptions) = update(id) {
        it?.copy(remoteOptions = options)
    }

    fun dismissRemoteSessions(id: Long) = update(id) {
        it?.copy(remoteOptions = null)
    }

    fun remove(id: Long) {
        _sessions.value = _sessions.value.filterNot { it.id == id }
    }

    fun find(id: Long): Session? = _sessions.value.firstOrNull { it.id == id }

    fun forHost(hostId: Long): List<Session> = _sessions.value.filter { it.hostId == hostId }

    private fun update(id: Long, block: (Session?) -> Session?) {
        val current = _sessions.value
        val existing = current.firstOrNull { it.id == id }
        val updated = block(existing) ?: return
        _sessions.value =
            if (existing == null) current + updated
            else current.map { if (it.id == id) updated else it }
    }
}
