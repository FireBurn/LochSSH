package uk.co.fireburn.lochssh.ui.hosts

import uk.co.fireburn.lochssh.data.db.SshHostEntity
import uk.co.fireburn.lochssh.ssh.SessionRegistry

internal data class HostSessionGroup(
    val hostId: Long,
    val name: String,
    val address: String?,
    val sessions: List<SessionRegistry.Session>,
    val hasTmux: Boolean = true
)

internal object HostSessionGroups {
    fun create(hosts: List<SshHostEntity>, sessions: List<SessionRegistry.Session>): List<HostSessionGroup> {
        val saved = hosts.associateBy { it.id }
        return sessions.groupBy { it.hostId }.map { (id, entries) ->
            val host = saved[id]
            HostSessionGroup(id, host?.name ?: entries.first().hostName,
                host?.let { "${it.host}:${it.port}" }, entries,
                hasTmux = host?.let { !it.telnet && !it.localShell && !it.serial } ?: false)
        }
    }
}
