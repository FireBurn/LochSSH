package uk.co.fireburn.lochssh.ssh

import java.io.Closeable

interface KeyInstaller : Closeable {
    fun install(host: String, port: Int, username: String, password: ByteArray, key: GeneratedKey)
}
