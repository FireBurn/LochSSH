package uk.co.fireburn.lochssh.ui.hosts

import uk.co.fireburn.lochssh.data.db.SshHostEntity
import uk.co.fireburn.lochssh.ssh.SavedSshProxy

data class HostProxyFields(
    val type: String = SavedSshProxy.NONE,
    val host: String = "",
    val port: String = "1080",
    val identityId: Long? = null
) {
    fun applyTo(host: SshHostEntity): SshHostEntity {
        if (type == SavedSshProxy.NONE) return host.copy(
            proxyType = type, proxyHost = "", proxyPort = 1080, proxyIdentityId = null)
        val number = port.toIntOrNull() ?: throw IllegalArgumentException("Enter a proxy port from 1 to 65535")
        return host.copy(proxyType = type, proxyHost = this.host.trim(), proxyPort = number,
            proxyIdentityId = identityId).also { SavedSshProxy.settings(it) }
    }

    companion object {
        fun from(host: SshHostEntity) = HostProxyFields(host.proxyType, host.proxyHost,
            host.proxyPort.toString(), host.proxyIdentityId)
    }
}
