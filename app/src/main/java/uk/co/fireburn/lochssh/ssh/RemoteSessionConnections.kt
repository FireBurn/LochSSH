package uk.co.fireburn.lochssh.ssh

internal object RemoteSessionConnections {
    data class Connection(val id: Long, val command: String, val connect: Boolean)

    fun plan(hostId: Long, entries: List<RemoteSessionEntry>, sessions: List<SessionRegistry.Session>,
        newId: () -> Long): List<Connection> = entries.distinctBy { it.tool to it.id }.map { entry ->
        val commands = setOf(RemoteSessions.attach(entry.copy(attached = false)),
            RemoteSessions.attach(entry.copy(attached = true)))
        val matches = sessions.filter { it.hostId == hostId && it.remoteCommand in commands }
        val existing = matches.firstOrNull { it.error == null && !it.canReconnect }
            ?: matches.firstOrNull()
        Connection(existing?.id ?: newId(), RemoteSessions.attach(entry),
            existing == null || existing.canReconnect)
    }
}
