package uk.co.fireburn.lochssh.ssh

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpATTRS
import com.jcraft.jsch.SftpException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

internal object AuthorizedKeysFile {
    fun add(sftp: ChannelSftp, publicKey: String, directory: String = "${sftp.home}/.ssh") {
        AuthorizedKeyText.addition(byteArrayOf(), publicKey)
        val directoryInfo = information(sftp, directory)
        if (directoryInfo == null) sftp.mkdir(directory)
        else require(directoryInfo.isDir && !directoryInfo.isLink) { "The .ssh path must be a real directory" }
        sftp.chmod(448, directory)
        val path = "$directory/authorized_keys"
        val info = information(sftp, path)
        require(info == null || (!info.isLink && info.isReg)) { "The authorized_keys path must be a regular file" }
        require(info == null || info.size <= AuthorizedKeyText.MAX_BYTES) { "The authorized_keys file is too large" }
        val existing = if (info == null) byteArrayOf() else sftp.get(path).use(::readKeys)
        val addition = AuthorizedKeyText.addition(existing, publicKey)
        require(existing.size + addition.size <= AuthorizedKeyText.MAX_BYTES) { "The authorized_keys file is too large" }
        if (addition.isNotEmpty()) {
            if (info == null) sftp.put(path, ChannelSftp.APPEND).use { }
            sftp.chmod(384, path)
            sftp.put(path, ChannelSftp.APPEND).use { it.write(addition) }
        }
        sftp.chmod(384, path)
        val installed = sftp.get(path).use(::readKeys)
        if (AuthorizedKeyText.addition(installed, publicKey).isNotEmpty()) {
            throw IOException("The server did not save the public key")
        }
    }

    private fun readKeys(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return output.toByteArray()
            require(output.size() + count <= AuthorizedKeyText.MAX_BYTES) { "The authorized_keys file is too large" }
            output.write(buffer, 0, count)
        }
    }

    private fun information(sftp: ChannelSftp, path: String): SftpATTRS? = try {
        sftp.lstat(path)
    } catch (e: SftpException) {
        if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) null else throw e
    }
}
