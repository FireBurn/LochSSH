package uk.co.fireburn.lochssh.ssh

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class SftpBrowserLocalTest {
    @Test
    fun transfersThroughAnOpenSshSession() {
        assumeTrue(System.getenv("LOCHSSH_TEST_SFTP") == "1")
        val home = File(System.getProperty("user.home"), ".ssh")
        val jsch = JSch().apply {
            setKnownHosts(File(home, "known_hosts").absolutePath)
            addIdentity(File(home, "id_ed25519").absolutePath)
        }
        val session = jsch.getSession(System.getProperty("user.name"), "localhost", 22)
        session.setConfig("StrictHostKeyChecking", "yes")
        session.setConfig("PreferredAuthentications", "publickey")
        session.connect(5_000)

        val name = "lochssh-sftp-${UUID.randomUUID()}.txt"
        val linkName = "$name-link"
        val first = "first".toByteArray()
        val second = "second".toByteArray()
        var created = false
        var linkCreated = false
        try {
            val browser = SftpBrowser(session)
            assertFalse(browser.exists("/tmp", name))
            browser.upload("/tmp", name, ByteArrayInputStream(first), false)
            created = true
            assertTrue(browser.exists("/tmp", name))
            assertTrue(browser.list("/tmp").files.any { it.name == name })
            val linkChannel = session.openChannel("sftp") as ChannelSftp
            try {
                linkChannel.connect(5_000)
                linkChannel.symlink("/tmp", "/tmp/$linkName")
                linkCreated = true
            } finally {
                linkChannel.disconnect()
            }
            assertTrue(browser.list("/tmp").files.any {
                it.name == linkName && it.isLink && it.isDirectory
            })

            val output = ByteArrayOutputStream()
            browser.download("/tmp", name, output)
            assertArrayEquals(first, output.toByteArray())
            assertThrows(IOException::class.java) {
                browser.upload("/tmp", name, ByteArrayInputStream(second), false)
            }
            browser.upload("/tmp", name, ByteArrayInputStream(second), true)
            val replaced = ByteArrayOutputStream()
            browser.download("/tmp", name, replaced)
            assertArrayEquals(second, replaced.toByteArray())
        } finally {
            if (created || linkCreated) {
                val channel = session.openChannel("sftp") as ChannelSftp
                try {
                    channel.connect(5_000)
                    if (linkCreated) channel.rm("/tmp/$linkName")
                    if (created) channel.rm("/tmp/$name")
                } finally {
                    channel.disconnect()
                }
            }
            session.disconnect()
        }
    }
}
