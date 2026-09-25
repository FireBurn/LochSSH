package uk.co.fireburn.lochssh.ssh

import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException

internal object IdentityLoader {
    fun add(jsch: JSch, path: String?, material: String?, passphrase: String?) {
        val passwordBytes = passphrase?.takeIf { it.isNotEmpty() }?.toByteArray(Charsets.UTF_8)
        val key = material?.toByteArray(Charsets.UTF_8)
        try {
            when {
                path != null && passwordBytes == null -> jsch.addIdentity(path)
                path != null -> jsch.addIdentity(path, passwordBytes)
                key != null -> jsch.addIdentity("LochSSH", key, null, passwordBytes)
            }
            if (jsch.identityRepository.identities.any { it.isEncrypted }) {
                val message = if (passwordBytes == null) {
                    "Private key needs a passphrase"
                } else {
                    "Could not unlock private key"
                }
                throw JSchException(message)
            }
        } finally {
            key?.fill(0)
            passwordBytes?.fill(0)
        }
    }
}
