package uk.co.fireburn.lochssh.ssh

import uk.co.fireburn.lochssh.data.db.AuthTypes
import uk.co.fireburn.lochssh.data.db.IdentityEntity
import uk.co.fireburn.lochssh.data.db.SshHostEntity

object SavedSshProxy {
    const val NONE = "NONE"

    fun settings(host: SshHostEntity): SshProxySettings? {
        if (host.proxyType == NONE) return null
        val type = SshProxyType.entries.find { it.name == host.proxyType }
            ?: throw IllegalArgumentException("Choose a proxy type")
        return SshProxySettings(type, host.proxyHost, host.proxyPort).also { it.validate() }
    }

    suspend fun checkIdentity(host: SshHostEntity, find: suspend (Long) -> IdentityEntity?): IdentityEntity? {
        val settings = settings(host) ?: return null
        val id = host.proxyIdentityId ?: return null
        val identity = requireNotNull(find(id)) { "The proxy identity no longer exists" }
        require(identity.authType == AuthTypes.PASSWORD && identity.username.isNotBlank() &&
            !identity.secretRef.isNullOrBlank()) { "Choose a password identity for the proxy" }
        settings.copy(username = identity.username, password = "check").validate()
        return identity
    }

    suspend fun resolve(host: SshHostEntity, find: suspend (Long) -> IdentityEntity?,
        readSecret: (String) -> String?): SshProxySettings? {
        val settings = settings(host) ?: return null
        val identity = checkIdentity(host, find) ?: return settings
        val password = requireNotNull(readSecret(requireNotNull(identity.secretRef))) { "The proxy password is missing" }
        return settings.copy(username = identity.username, password = password).also { it.validate() }
    }

    fun checkRoute(chain: List<SshHostEntity>) {
        chain.forEachIndexed { index, host ->
            if (settings(host) != null) {
                require(index == 0) { "Set the proxy on the first jump host, not on a host reached through SSH" }
                require(host.knockSequence.isBlank()) { "Port knocking cannot run through a proxy" }
            }
        }
    }
}
