package uk.co.fireburn.lochssh.ui.hosts

import uk.co.fireburn.lochssh.data.db.AuthTypes
import uk.co.fireburn.lochssh.data.db.IdentityEntity

internal object HostIdentity {
    suspend fun check(
        id: Long?,
        agentForwarding: Boolean,
        find: suspend (Long) -> IdentityEntity?
    ) {
        val identity = id?.let {
            requireNotNull(find(it)) { "The selected identity no longer exists" }
        }
        require(!agentForwarding || identity?.authType == AuthTypes.PUBLIC_KEY) {
            "Choose a private-key identity for agent forwarding"
        }
    }
}
