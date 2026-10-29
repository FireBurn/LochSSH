package uk.co.fireburn.lochssh.ssh

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URLDecoder
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

// name is the last good label the session had, shown again while it reconnects.
data class RememberedSession(val hostId: Long, val remoteCommand: String?, val savedAt: Long, val name: String? = null) {
    fun encode(): String = "$hostId ${field(remoteCommand)} $savedAt" + (name?.let { " " + field(it) } ?: "")

    companion object {
        fun decode(text: String): RememberedSession? {
            val parts = text.split(' ')
            if (parts.size != 3 && parts.size != 4) return null
            val hostId = parts[0].toLongOrNull() ?: return null
            val savedAt = parts[2].toLongOrNull() ?: return null
            val command = when {
                parts[1] == "!" -> null
                parts[1].startsWith("=") -> URLDecoder.decode(parts[1].drop(1), "UTF-8")
                else -> return null
            }
            val name = if (parts.size == 4) {
                if (!parts[3].startsWith("=")) return null
                URLDecoder.decode(parts[3].drop(1), "UTF-8")
            } else null
            return RememberedSession(hostId, command, savedAt, name)
        }

        private fun field(value: String?) = if (value == null) "!" else "=" + URLEncoder.encode(value, "UTF-8")
    }
}

// Open sessions are kept only in memory, so Android freeing the app loses them. This keeps
// where each one was so the app can reconnect to it when it is opened again.
@Singleton
class SessionMemory @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("lochssh_sessions", Context.MODE_PRIVATE)

    fun remember(id: Long, hostId: Long, remoteCommand: String?) {
        val name = find(id)?.name
        preferences.edit().putString(id.toString(), RememberedSession(hostId, remoteCommand,
            System.currentTimeMillis(), name).encode()).apply()
    }

    fun rename(id: Long, name: String) {
        val saved = find(id) ?: return
        if (saved.name == name) return
        preferences.edit().putString(id.toString(), saved.copy(name = name).encode()).apply()
    }

    fun forget(id: Long) = preferences.edit().remove(id.toString()).apply()

    fun find(id: Long): RememberedSession? {
        val saved = preferences.getString(id.toString(), null)?.let(RememberedSession::decode) ?: return null
        if (System.currentTimeMillis() - saved.savedAt > MAX_AGE_MS) {
            forget(id)
            return null
        }
        return saved
    }

    fun all(): Map<Long, RememberedSession> = preferences.all.keys
        .mapNotNull { key -> key.toLongOrNull()?.let { id -> find(id)?.let { id to it } } }
        .toMap()

    fun view(id: Long?) = preferences.edit().putLong(LAST_VIEWED, id ?: -1L).apply()

    fun lastViewed(): Long? = preferences.getLong(LAST_VIEWED, -1L).takeIf { it > 0 }

    private companion object {
        const val LAST_VIEWED = "last_viewed"
        const val MAX_AGE_MS = 3L * 24 * 60 * 60 * 1000
    }
}
