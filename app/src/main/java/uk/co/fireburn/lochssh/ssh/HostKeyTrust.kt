package uk.co.fireburn.lochssh.ssh

import android.content.Context
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.UserInfo
import java.io.File
import java.io.IOException

object HostKeyTrust {
    private var store: PersistentHostKeys? = null

    @Synchronized
    fun repository(context: Context): HostKeyRepository =
        store ?: PersistentHostKeys(File(context.filesDir, "known_hosts")).also { store = it }
}

internal class PersistentHostKeys(private val file: File) : HostKeyRepository {
    private val backing: HostKeyRepository
    private var usable = true

    init {
        if (!file.exists() && !file.createNewFile() && !file.exists()) {
            throw IOException("Could not create known hosts file")
        }
        val jsch = JSch()
        jsch.setKnownHosts(file.absolutePath)
        backing = jsch.hostKeyRepository
    }

    @Synchronized
    override fun check(host: String, key: ByteArray): Int {
        check(usable) { "Known hosts file could not be saved" }
        val result = backing.check(host, key)
        if (result != HostKeyRepository.NOT_INCLUDED) return result
        if (backing.getHostKey(host, null).isNotEmpty()) return HostKeyRepository.CHANGED

        backing.add(HostKey(host, key), null)
        val saved = JSch().apply { setKnownHosts(file.absolutePath) }.hostKeyRepository
        if (saved.check(host, key) != HostKeyRepository.OK) {
            usable = false
            throw IOException("Could not save host key")
        }
        return HostKeyRepository.OK
    }

    @Synchronized
    override fun add(hostkey: HostKey, ui: UserInfo?) = backing.add(hostkey, ui)

    @Synchronized
    override fun remove(host: String?, type: String?) = backing.remove(host, type)

    @Synchronized
    override fun remove(host: String?, type: String?, key: ByteArray?) =
        backing.remove(host, type, key)

    override fun getKnownHostsRepositoryID(): String = backing.knownHostsRepositoryID

    @Synchronized
    override fun getHostKey(): Array<HostKey> = backing.hostKey

    @Synchronized
    override fun getHostKey(host: String?, type: String?): Array<HostKey> =
        backing.getHostKey(host, type)
}
