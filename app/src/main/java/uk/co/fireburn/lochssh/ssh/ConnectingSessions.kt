package uk.co.fireburn.lochssh.ssh

import com.jcraft.jsch.Session
import com.jcraft.jsch.Proxy
import java.io.InterruptedIOException

internal class ConnectingSessions {
    private data class Pending(val session: Session, val proxy: Proxy?)
    private val lock = Any()
    private val sessions = mutableListOf<Pending>()
    private var closed = false

    fun add(session: Session, proxy: Proxy? = null) {
        val pending = Pending(session, proxy)
        synchronized(lock) {
            if (!closed) { sessions += pending; return }
        }
        close(pending)
        throw InterruptedIOException("Connection cancelled")
    }

    fun clear() { synchronized(lock) { sessions.clear() } }

    fun close() {
        val pending = synchronized(lock) { closed = true; sessions.toList() }
        pending.asReversed().forEach(::close)
    }

    private fun close(pending: Pending) {
        // JSch disconnect ignores a session until its proxy handshake finishes.
        runCatching { pending.proxy?.close() }
        runCatching { pending.session.disconnect() }
    }
}
