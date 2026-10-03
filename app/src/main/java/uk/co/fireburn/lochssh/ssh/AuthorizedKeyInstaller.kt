package uk.co.fireburn.lochssh.ssh

import android.content.Context
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.android.AndroidJSch
import java.io.IOException

class AuthorizedKeyInstaller(private val context: Context) : KeyInstaller {
    private val lock = Any()
    private var closed = false
    private var active: Session? = null

    override fun install(host: String, port: Int, username: String, password: ByteArray, key: GeneratedKey) {
        try {
            require(host.isNotBlank() && username.isNotBlank() && port in 1..65535) { "Enter a host, port and username" }
            require(password.isNotEmpty()) { "Enter the server password" }
            AndroidJSch.configure()
            connected(host, port, username, password, null) { session ->
                val sftp = session.openChannel("sftp") as ChannelSftp
                try {
                    sftp.connect(10_000)
                    AuthorizedKeysFile.add(sftp, key.publicKeyOpenSsh)
                } finally { sftp.disconnect() }
            }
            try {
                connected(host, port, username, null, key.privateKeyPem) { }
            } catch (e: Exception) {
                throw IOException("The public key was added, but key login failed. Check the server's SSH settings before changing the host identity.", e)
            }
        } finally { password.fill(0) }
    }

    private fun connected(host: String, port: Int, username: String, password: ByteArray?,
        privateKey: String?, use: (Session) -> Unit) {
        val ssh = JSch().apply { setHostKeyRepository(HostKeyTrust.repository(context)) }
        if (privateKey != null) IdentityLoader.add(ssh, null, privateKey, null)
        val session = ssh.getSession(username, host, port)
        session.setConfig("StrictHostKeyChecking", "yes")
        session.setConfig("PreferredAuthentications", if (password != null) "password" else "publickey")
        session.setTimeout(10_000)
        password?.let(session::setPassword)
        try {
            synchronized(lock) {
                check(!closed) { "Key installation cancelled" }
                active = session
            }
            session.connect(10_000)
            synchronized(lock) { check(!closed) { "Key installation cancelled" } }
            use(session)
        } finally {
            session.setPassword(byteArrayOf())
            session.disconnect()
            ssh.removeAllIdentity()
            synchronized(lock) { if (active === session) active = null }
        }
    }

    override fun close() {
        val session = synchronized(lock) { closed = true; active }
        session?.disconnect()
    }
}
