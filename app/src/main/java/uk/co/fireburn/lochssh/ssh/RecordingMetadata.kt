package uk.co.fireburn.lochssh.ssh

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS

internal data class RecordingMetadata(val hostLabel: String, val startedAt: Long) {
    fun write(file: File) {
        require(hostLabel.length <= 512 && startedAt > 0)
        check(file.createNewFile()) { "Could not create recording details" }
        try {
            DataOutputStream(file.outputStream()).use {
                it.writeInt(1)
                it.writeLong(startedAt)
                it.writeUTF(hostLabel)
            }
        } catch (error: Exception) { file.delete(); throw error }
    }

    companion object {
        fun read(file: File): RecordingMetadata? = runCatching {
            if (!Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS) || file.length() !in 14..2048) return null
            DataInputStream(file.inputStream()).use {
                check(it.readInt() == 1)
                val startedAt = it.readLong()
                val label = it.readUTF()
                check(startedAt > 0 && label.length <= 512 && it.read() == -1)
                RecordingMetadata(label, startedAt)
            }
        }.getOrNull()
    }
}
