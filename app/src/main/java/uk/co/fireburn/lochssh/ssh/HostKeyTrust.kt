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

    fun saved(context: Context): List<SavedHostKey> =
        (repository(context) as PersistentHostKeys).saved()

    fun remove(context: Context, key: SavedHostKey) =
        (repository(context) as PersistentHostKeys).removeSaved(key)

    fun accept(context: Context, key: SavedHostKey) =
        (repository(context) as PersistentHostKeys).accept(key)

    @Synchronized
    fun clear(context: Context) {
        store?.clear() ?: PersistentHostKeys(File(context.filesDir, "known_hosts")).clear()
    }
}

data class SavedHostKey(
    val host: String,
    val type: String,
    val fingerprint: String,
    val key: String
)

class HostKeyReviewRequired(val key: SavedHostKey) : RuntimeException("Server key needs approval for ${key.host}")

internal class PersistentHostKeys(private val file: File) : HostKeyRepository {
    private var backing: HostKeyRepository
    private var usable = true

    init {
        if (!file.exists() && !file.createNewFile() && !file.exists()) {
            throw IOException("Could not create known hosts file")
        }
        backing = reload()
    }

    private fun reload(): HostKeyRepository =
        JSch().apply { setKnownHosts(file.absolutePath) }.hostKeyRepository

    @Synchronized
    fun saved(): List<SavedHostKey> = backing.hostKey.map { key ->
        SavedHostKey(key.host, key.type, key.getFingerPrint(JSch()), key.key)
    }

    @Synchronized
    fun removeSaved(key: SavedHostKey) {
        val match = backing.hostKey.firstOrNull {
            it.host == key.host && it.type == key.type && it.key == key.key
        } ?: return
        backing.remove(match.host, match.type, java.util.Base64.getDecoder().decode(match.key))
        backing = reload()
        if (backing.hostKey.any {
                it.host == key.host && it.type == key.type && it.key == key.key
            }) {
            throw IOException("Could not remove host key")
        }
    }

    @Synchronized
    fun clear() {
        if (file.exists() && !file.delete()) throw IOException("Could not clear host keys")
        if (!file.createNewFile() && !file.exists()) throw IOException("Could not clear host keys")
        backing = reload()
        usable = true
    }

    @Synchronized
    override fun check(host: String, key: ByteArray): Int {
        check(usable) { "Known hosts file could not be saved" }
        val result = backing.check(host, key)
        if (result != HostKeyRepository.NOT_INCLUDED) return result
        if (backing.getHostKey(host, null).isNotEmpty()) return HostKeyRepository.CHANGED

        val candidate = HostKey(host, key)
        throw HostKeyReviewRequired(SavedHostKey(host, candidate.type, candidate.getFingerPrint(JSch()), candidate.key))
    }

    @Synchronized
    fun accept(key: SavedHostKey) {
        check(usable) { "Known hosts file could not be saved" }
        val bytes = java.util.Base64.getDecoder().decode(key.key)
        val candidate = HostKey(key.host, bytes)
        require(candidate.type == key.type && candidate.getFingerPrint(JSch()) == key.fingerprint) {
            "Server key does not match the reviewed fingerprint"
        }
        val result = backing.check(key.host, bytes)
        if (result == HostKeyRepository.OK) return
        require(result == HostKeyRepository.NOT_INCLUDED && backing.getHostKey(key.host, null).isEmpty()) {
            "A different server key has already been saved"
        }
        backing.add(candidate, null)
        val saved = reload()
        if (saved.check(key.host, bytes) != HostKeyRepository.OK) {
            usable = false
            throw IOException("Could not save host key")
        }
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
