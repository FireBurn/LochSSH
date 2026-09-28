package uk.co.fireburn.lochssh.ssh

import com.jcraft.jsch.Session
import java.util.concurrent.CopyOnWriteArrayList

internal class ConnectingSessions {
    private val sessions = CopyOnWriteArrayList<Session>()

    fun add(session: Session) { sessions += session }
    fun clear() { sessions.clear() }

    fun close() {
        sessions.toList().asReversed().forEach { runCatching { it.disconnect() } }
    }
}
