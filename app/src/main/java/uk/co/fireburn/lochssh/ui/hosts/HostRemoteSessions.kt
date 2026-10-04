package uk.co.fireburn.lochssh.ui.hosts

import uk.co.fireburn.lochssh.ssh.SessionRegistry

internal object HostRemoteSessions {
    fun sessionId(hostId: Long, sessions: List<SessionRegistry.Session>, newId: () -> Long): Long? {
        if (sessions.any { it.hostId == hostId && it.hostKeyReview != null }) return null
        val scans = sessions.filter { it.hostId == hostId && it.findRemoteSessions }
        if (scans.any { it.hostKeyReview != null || (it.manager == null && it.error == null) }) return null
        return scans.firstOrNull { it.canReconnect }?.id ?: newId()
    }
}
