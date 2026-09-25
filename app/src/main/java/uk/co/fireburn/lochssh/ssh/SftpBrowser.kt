package uk.co.fireburn.lochssh.ssh

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

data class RemoteFile(
    val name: String,
    val isDirectory: Boolean,
    val isLink: Boolean,
    val size: Long
)

data class RemoteDirectory(val path: String, val files: List<RemoteFile>)

object RemotePath {
    fun child(directory: String, name: String): String {
        require(name.isNotEmpty() && name != "." && name != ".." &&
            '/' !in name && '\u0000' !in name) { "Invalid file name" }
        return if (directory == "/") "/$name" else "${directory.trimEnd('/')}/$name"
    }

    fun parent(path: String): String? {
        val clean = path.trimEnd('/')
        if (clean.isEmpty() || clean == ".") return null
        return clean.substringBeforeLast('/', "").ifEmpty { "/" }
    }
}

class SftpBrowser(private val session: Session) {
    fun list(path: String): RemoteDirectory = withChannel { channel ->
        channel.cd(path)
        val current = channel.pwd()
        val files = channel.ls(".").asSequence()
            .filter { it.filename != "." && it.filename != ".." }
            .map {
                val directory = if (it.attrs.isLink) {
                    try {
                        channel.stat(RemotePath.child(current, it.filename)).isDir
                    } catch (_: SftpException) {
                        false
                    }
                } else it.attrs.isDir
                RemoteFile(it.filename, directory, it.attrs.isLink, it.attrs.size)
            }
            .sortedWith(compareByDescending<RemoteFile> { it.isDirectory }
                .thenBy { it.name.lowercase() })
            .toList()
        RemoteDirectory(current, files)
    }

    fun exists(directory: String, name: String): Boolean = withChannel { channel ->
        channel.cd(directory)
        val remotePath = RemotePath.child(channel.pwd(), name)
        try {
            channel.lstat(remotePath)
            true
        } catch (e: SftpException) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) false else throw e
        }
    }

    fun download(directory: String, name: String, output: OutputStream) = withChannel { channel ->
        channel.cd(directory)
        channel.get(RemotePath.child(channel.pwd(), name), output)
    }

    fun upload(directory: String, name: String, input: InputStream, overwrite: Boolean) =
        withChannel { channel ->
            channel.cd(directory)
            val remotePath = RemotePath.child(channel.pwd(), name)
            if (!overwrite) {
                try {
                    channel.lstat(remotePath)
                    throw IOException("Remote file already exists")
                } catch (e: SftpException) {
                    if (e.id != ChannelSftp.SSH_FX_NO_SUCH_FILE) throw e
                }
            }
            channel.put(input, remotePath)
        }

    private fun <T> withChannel(block: (ChannelSftp) -> T): T {
        if (!session.isConnected) throw IOException("SSH session closed")
        val channel = session.openChannel("sftp") as ChannelSftp
        try {
            channel.connect(10_000)
            return block(channel)
        } finally {
            channel.disconnect()
        }
    }
}
