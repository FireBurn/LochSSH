package uk.co.fireburn.lochssh.ssh

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import java.io.File
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Test

class AuthorizedKeysFileLocalTest {
    @Test fun preservesEntriesPermissionsAndRestrictionsOnAnSftpServer() = withServer { sftp, root ->
        val directory = "$root/.ssh"
        val first = KeyGenerator.generate("first").publicKeyOpenSsh
        val second = KeyGenerator.generate("phone").publicKeyOpenSsh
        AuthorizedKeysFile.add(sftp, first, directory)
        val existing = "# Keep this\nrestrict $first"
        sftp.put("$directory/authorized_keys").use { it.write(existing.toByteArray()) }
        AuthorizedKeysFile.add(sftp, second, directory)
        assertEquals("$existing\n$second\n", sftp.get("$directory/authorized_keys").use { it.readBytes().toString(Charsets.UTF_8) })
        AuthorizedKeysFile.add(sftp, first.substringBeforeLast(' ') + " new-comment", directory)
        AuthorizedKeysFile.add(sftp, second, directory)
        assertEquals("$existing\n$second\n", sftp.get("$directory/authorized_keys").use { it.readBytes().toString(Charsets.UTF_8) })
        assertEquals(448, sftp.stat(directory).permissions and 511)
        assertEquals(384, sftp.stat("$directory/authorized_keys").permissions and 511)
    }

    @Test fun refusesToFollowAKeysFileSymlink() = withServer { sftp, root ->
        val directory = "$root/.ssh"
        sftp.mkdir(directory)
        sftp.put("$root/keep").use { it.write("untouched".toByteArray()) }
        sftp.symlink("$root/keep", "$directory/authorized_keys")
        assertThrows(IllegalArgumentException::class.java) {
            AuthorizedKeysFile.add(sftp, KeyGenerator.generate().publicKeyOpenSsh, directory)
        }
        assertEquals("untouched", sftp.get("$root/keep").use { it.readBytes().toString(Charsets.UTF_8) })
    }

    @Test fun refusesToFollowASshDirectorySymlink() = withServer { sftp, root ->
        sftp.mkdir("$root/keep")
        sftp.symlink("$root/keep", "$root/.ssh")
        assertThrows(IllegalArgumentException::class.java) {
            AuthorizedKeysFile.add(sftp, KeyGenerator.generate().publicKeyOpenSsh, "$root/.ssh")
        }
        assertEquals(0, sftp.ls("$root/keep").count { (it as ChannelSftp.LsEntry).filename !in listOf(".", "..") })
    }

    @Test fun rejectsAnOversizedFileWithoutChangingItsContents() = withServer { sftp, root ->
        val directory = "$root/.ssh"
        sftp.mkdir(directory)
        val path = "$directory/authorized_keys"
        val existing = ByteArray(AuthorizedKeyText.MAX_BYTES + 1) { '#'.code.toByte() }
        sftp.put(path).use { it.write(existing) }
        assertThrows(IllegalArgumentException::class.java) {
            AuthorizedKeysFile.add(sftp, KeyGenerator.generate().publicKeyOpenSsh, directory)
        }
        assertArrayEquals(existing, sftp.get(path).use { it.readBytes() })
    }

    @Test fun rejectsAnAdditionOverTheLimitWithoutChangingExistingKeys() = withServer { sftp, root ->
        val directory = "$root/.ssh"
        sftp.mkdir(directory)
        val path = "$directory/authorized_keys"
        val key = KeyGenerator.generate().publicKeyOpenSsh
        val existing = ByteArray(AuthorizedKeyText.MAX_BYTES) { '#'.code.toByte() }
        sftp.put(path).use { it.write(existing) }
        assertThrows(IllegalArgumentException::class.java) {
            AuthorizedKeysFile.add(sftp, key, directory)
        }
        assertArrayEquals(existing, sftp.get(path).use { it.readBytes() })
    }

    @Test fun rejectsADirectoryWhereTheKeysFileShouldBe() = withServer { sftp, root ->
        val directory = "$root/.ssh"
        sftp.mkdir(directory)
        val path = "$directory/authorized_keys"
        sftp.mkdir(path)
        try {
            assertThrows(IllegalArgumentException::class.java) {
                AuthorizedKeysFile.add(sftp, KeyGenerator.generate().publicKeyOpenSsh, directory)
            }
            assertEquals(0, sftp.ls(path).count { (it as ChannelSftp.LsEntry).filename !in listOf(".", "..") })
        } finally { sftp.rmdir(path) }
    }

    private fun withServer(test: (ChannelSftp, String) -> Unit) {
        assumeTrue(System.getenv("LOCHSSH_TEST_SFTP") == "1")
        val ssh = JSch().apply {
            setKnownHosts(File(System.getProperty("user.home"), ".ssh/known_hosts").absolutePath)
            addIdentity(requireNotNull(System.getenv("LOCHSSH_TEST_SSH_KEY")))
        }
        val session = ssh.getSession(requireNotNull(System.getenv("LOCHSSH_TEST_SSH_USER")),
            requireNotNull(System.getenv("LOCHSSH_TEST_SSH_HOST")), 22)
        session.setConfig("StrictHostKeyChecking", "yes")
        session.setConfig("PreferredAuthentications", "publickey")
        session.setTimeout(5_000)
        session.connect(5_000)
        val sftp = session.openChannel("sftp") as ChannelSftp
        val root = "/tmp/lochssh-key-test-${UUID.randomUUID()}"
        try {
            sftp.connect(5_000)
            sftp.mkdir(root)
            test(sftp, root)
        } finally {
            try {
                for (name in listOf(".ssh", "keep")) {
                    val path = "$root/$name"
                    val info = try { sftp.lstat(path) } catch (_: com.jcraft.jsch.SftpException) { null }
                    if (info?.isLink == true || info?.isReg == true) sftp.rm(path)
                    else if (info?.isDir == true) {
                        for (entry in sftp.ls(path)) {
                            val file = (entry as ChannelSftp.LsEntry).filename
                            if (file != "." && file != "..") sftp.rm("$path/$file")
                        }
                        sftp.rmdir(path)
                    }
                }
                sftp.rmdir(root)
            } finally { sftp.disconnect(); session.disconnect() }
        }
    }
}
