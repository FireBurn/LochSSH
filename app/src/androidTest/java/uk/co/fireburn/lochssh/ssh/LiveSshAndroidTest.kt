package uk.co.fireburn.lochssh.ssh

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import uk.co.fireburn.lochssh.data.db.AuthTypes

// Needs a reachable test server: pass host, port, user, ed25519Key and rsaKey (file paths
// readable by the app) as instrumentation arguments. Skipped when they are missing.
class LiveSshAndroidTest {
    private val arguments = InstrumentationRegistry.getArguments()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun requireServer() {
        assumeTrue(arguments.getString("host") != null && arguments.getString("ed25519Key") != null)
        HostKeyTrust.clear(context)
    }

    @Test
    fun ed25519KeyConnectsAfterHostKeyApproval() = exercise("ed25519Key")

    @Test
    fun rsaKeyConnects() = exercise("rsaKey")

    private fun exercise(keyArgument: String) {
        val manager = connect(File(arguments.getString(keyArgument)!!).readText())
        try {
            assertTrue(manager.isConnected)
            manager.write("echo LOCH_$((6*7))\n".toByteArray())
            waitFor("shell output") { screen(manager).contains("LOCH_42") }

            val sessions = manager.discoverRemoteSessions()
            assertTrue(sessions.tmuxAvailable)
            assertTrue(sessions.toString(), sessions.sessions.any { it.name.startsWith("lochtest") })

            val performance = manager.readPerformance()
            assertNotNull(performance.memory)
            assertTrue(performance.load.isNotEmpty())

            val listing = manager.sftpBrowser().list(".")
            assertTrue(listing.files.isNotEmpty())
        } finally {
            manager.disconnect()
        }
    }

    private fun connect(key: String): SshConnectionManager {
        val config = SshConnectionConfig(
            host = arguments.getString("host")!!,
            port = arguments.getString("port")!!.toInt(),
            username = arguments.getString("user")!!,
            authType = AuthTypes.PUBLIC_KEY,
            password = null, keyPath = null, keyMaterial = key, keyPassphrase = null,
            keepAliveSeconds = 30
        )
        val exited = CountDownLatch(1)
        repeat(2) { attempt ->
            val manager = SshConnectionManager(context, config) { exited.countDown() }
            try {
                manager.connect()
                return manager
            } catch (e: Throwable) {
                val review = generateSequence(e) { it.cause }.filterIsInstance<HostKeyReviewRequired>().firstOrNull()
                if (review == null || attempt > 0) throw e
                HostKeyTrust.accept(context, review.key)
            }
        }
        fail("unreachable")
        throw IllegalStateException()
    }

    private fun screen(manager: SshConnectionManager): String {
        val buffer = manager.terminal.buffer
        return (0 until buffer.rows).joinToString("\n") { row ->
            (0 until buffer.cols).joinToString("") { buffer.cellAt(row, it).text }
        }
    }

    private fun waitFor(what: String, check: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (System.nanoTime() < deadline) {
            if (check()) return
            Thread.sleep(200)
        }
        assertEquals("timed out waiting for $what", true, check())
    }
}
