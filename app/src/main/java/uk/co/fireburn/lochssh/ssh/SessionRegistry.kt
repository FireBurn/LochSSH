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
        val remoteOptions: RemoteSessionOptions? = null,
        val forwardWarnings: List<String> = emptyList(),
        val hostKeyReview: SavedHostKey? = null,
        val remoteCommand: String? = null,
        val remoteName: String? = null,
        val findRemoteSessions: Boolean = false
    ) {
        val label: String get() {
            val remote = remoteName?.takeIf { it.isNotBlank() } ?: nameFromCommand()
            val live = title?.takeIf {
                it.isNotBlank() && (remote == null || (it != hostName && it != remote && !remote.endsWith(" · $it")))
            }
            return listOfNotNull(remote, live).distinct().joinToString(" · ").ifBlank { hostName }
        }

        // A tmux or Screen session opened without a name from a scan still has one in its command.
        private fun nameFromCommand(): String? {
            val target = Multiplexer.parse(remoteCommand)?.target ?: return null
            return if (target.startsWith("$")) "tmux ${target.drop(1)}" else target.substringAfter('.')
        }

        val isConnected: Boolean get() = manager?.isConnected == true
        val notificationTitle: String get() = listOf(hostName, label)
            .map { it.filterNot { char -> char.code < 32 || char.code == 127 }.trim().take(160) }
            .filter { it.isNotBlank() }.distinct().joinToString(" · ") + " · #$id"
        val canReconnect: Boolean get() = !isConnected && (error != null || manager != null) && hostKeyReview == null

        val statusText: String get() = when {
            hostKeyReview != null -> "Server key approval needed"
            error != null -> error
            findRemoteSessions && manager == null -> "Finding remote sessions"
            manager == null -> "Connecting"
            !isConnected -> "Disconnected"
            forwardWarnings.isNotEmpty() -> "Port forwarding issue"
            else -> "Connected"
        }

        val actionLabel: String get() = when {
            hostKeyReview != null -> "Review"
            isConnected -> "Resume"
            else -> "View"
        }
    }

    private val nextId = AtomicLong(1)
    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions
    private val _replacements = MutableStateFlow<Map<Long, Long>>(emptyMap())
    val replacements: StateFlow<Map<Long, Long>> = _replacements

    fun replaced(sourceId: Long, targetId: Long) {
        _replacements.value = (_replacements.value + (sourceId to targetId)).entries.toList().takeLast(100)
            .associate { it.key to it.value }
    }

    fun newId(): Long = nextId.getAndIncrement()

    // Sessions restored from an earlier run keep their ids, so new ones must start above them.
    fun reserveIdsThrough(id: Long) { nextId.updateAndGet { maxOf(it, id + 1) } }

    fun opening(id: Long, hostId: Long, hostName: String, remoteCommand: String? = null,
        findRemoteSessions: Boolean? = null) = update(id) {
        Session(id = id, hostId = hostId, hostName = hostName,
            remoteCommand = remoteCommand ?: it?.remoteCommand, remoteName = it?.remoteName,
            findRemoteSessions = findRemoteSessions ?: it?.findRemoteSessions ?: false)
    }

    fun connected(id: Long, manager: SshConnectionManager) = update(id) {
        it?.copy(manager = manager, error = null, hostKeyReview = null)
    }

    fun titled(id: Long, title: String) = update(id) {
        if (it?.title == title) null else it?.copy(title = title)
    }

    fun named(id: Long, name: String) = update(id) { it?.copy(remoteName = name) }

    fun failed(id: Long, message: String, review: SavedHostKey? = null) = update(id) {
        it?.copy(manager = null, error = message, hostKeyReview = review)
    }

    fun dismissHostKeyReview(id: Long) = update(id) { it?.copy(hostKeyReview = null) }

    fun offerRemoteSessions(id: Long, options: RemoteSessionOptions) = update(id) {
        it?.copy(remoteOptions = options)
    }

    fun dismissRemoteSessions(id: Long) = update(id) {
        it?.copy(remoteOptions = null)
    }

    fun addForwardWarning(id: Long, message: String) = update(id) {
        if (it == null || it.forwardWarnings.lastOrNull() == message) null
        else it.copy(forwardWarnings = (it.forwardWarnings + message).takeLast(5))
    }

    fun dismissForwardWarnings(id: Long) = update(id) {
        it?.copy(forwardWarnings = emptyList())
    }

    fun remove(id: Long) {
        _sessions.value = _sessions.value.filterNot { it.id == id }
    }

    fun closeSessions(ids: Set<Long>, error: String? = null) {
        _sessions.value = _sessions.value.mapNotNull {
            when {
                it.id !in ids -> it
                error == null -> null
                else -> it.copy(manager = null, error = error, remoteOptions = null, hostKeyReview = null)
            }
        }
    }

    fun find(id: Long): Session? = _sessions.value.firstOrNull { it.id == id }

    fun forHost(hostId: Long): List<Session> = _sessions.value.filter { it.hostId == hostId }

    private fun update(id: Long, block: (Session?) -> Session?) {
        val current = _sessions.value
        val existing = current.firstOrNull { it.id == id }
        val updated = block(existing) ?: return
        _sessions.value = ordered(
            if (existing == null) current + updated
            else current.map { if (it.id == id) updated else it }
        )
    }

    // Same order as the host list (by name), then by when each session was opened, so the screen
    // does not shuffle when sessions are restored or reconnect.
    private fun ordered(list: List<Session>): List<Session> =
        list.sortedWith(compareBy<Session, String>(String.CASE_INSENSITIVE_ORDER) { it.hostName }.thenBy { it.id })
}
