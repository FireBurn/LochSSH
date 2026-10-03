package uk.co.fireburn.lochssh.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors

class PerformanceCommandTest {
    @Test fun readsTheLocalLinuxServerWithoutInstallingAnything() {
        assumeTrue(File("/proc/stat").canRead())
        val process = ProcessBuilder("/bin/sh", "-c", PerformanceCommand.command).start()
        val reader = Executors.newSingleThreadExecutor()
        try {
            val output = reader.submit<String> {
                process.inputStream.readNBytes(PerformanceParser.MAX_OUTPUT + 1).toString(Charsets.UTF_8)
            }.get(10, TimeUnit.SECONDS)
            assertTrue(process.waitFor(10, TimeUnit.SECONDS))
            assertTrue(process.exitValue() == 0)
            val reading = PerformanceParser.parse(output)
            assertNotNull(reading.cpu)
            assertNotNull(reading.memory)
            assertNotNull(reading.disk)
            assertTrue(reading.load.size == 3)
        } finally { process.destroyForcibly(); reader.shutdownNow() }
    }

    @Test fun readsPerformanceThroughAnSshCommandChannel() {
        val host = System.getenv("LOCHSSH_TEST_SSH_HOST")
        val user = System.getenv("LOCHSSH_TEST_SSH_USER")
        val key = System.getenv("LOCHSSH_TEST_SSH_KEY")
        assumeTrue(!host.isNullOrBlank() && !user.isNullOrBlank() && !key.isNullOrBlank())
        val ssh = JSch().apply {
            setKnownHosts(File(System.getProperty("user.home"), ".ssh/known_hosts").absolutePath)
            addIdentity(requireNotNull(key))
        }
        val session = ssh.getSession(requireNotNull(user), requireNotNull(host), 22)
        session.setConfig("StrictHostKeyChecking", "yes")
        session.setConfig("PreferredAuthentications", "publickey")
        session.setTimeout(5_000)
        session.connect(5_000)
        try {
            val channel = session.openChannel("exec") as ChannelExec
            try {
                channel.setCommand(PerformanceCommand.command)
                val input = channel.inputStream
                channel.connect(5_000)
                val output = input.readNBytes(PerformanceParser.MAX_OUTPUT + 1).toString(Charsets.UTF_8)
                val reading = PerformanceParser.parse(output)
                assertNotNull(reading.cpu)
                assertNotNull(reading.memory)
                assertNotNull(reading.uptimeSeconds)
                assertTrue(reading.network.isNotEmpty())
            } finally { channel.disconnect() }
        } finally { session.disconnect() }
    }
}
