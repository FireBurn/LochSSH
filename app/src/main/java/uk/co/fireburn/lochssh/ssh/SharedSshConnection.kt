package uk.co.fireburn.lochssh.ssh

import com.jcraft.jsch.Session

internal class SharedSshConnection(
    val session: Session,
    private val jumps: List<Session>,
    private val forwards: List<Socks5Forwarder>
) {
    private var users = 1
    val isConnected: Boolean get() = session.isConnected

    @Synchronized
    fun acquire(): Boolean {
        if (users == 0 || !session.isConnected) return false
        users++
        return true
    }

    @Synchronized
    fun release() {
        if (users == 0 || --users != 0) return
        forwards.forEach { runCatching { it.stop() } }
        runCatching { session.disconnect() }
        jumps.asReversed().forEach { runCatching { it.disconnect() } }
    }
}
