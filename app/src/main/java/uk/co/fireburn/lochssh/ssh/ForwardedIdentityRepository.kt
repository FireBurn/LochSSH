package uk.co.fireburn.lochssh.ssh

import com.jcraft.jsch.Identity
import com.jcraft.jsch.IdentityRepository
import java.util.Vector

internal class ForwardedIdentityRepository(private val source: IdentityRepository) : IdentityRepository {
    override fun getName(): String = "LochSSH"
    override fun getStatus(): Int = source.status
    override fun getIdentities(): Vector<Identity> = Vector(source.identities)
    override fun add(identity: ByteArray): Boolean = false
    override fun remove(blob: ByteArray): Boolean = false
    override fun removeAll() = Unit
}
