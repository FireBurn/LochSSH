package uk.co.fireburn.lochssh.ssh

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
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
import uk.co.fireburn.lochssh.data.db.ForwardTypes

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

    @Test
    fun localForwardReachesTheServerThroughTheTunnel() {
        val forward = PortForwardSpec(ForwardTypes.LOCAL, 18222, "127.0.0.1", serverPort())
        withConnection(listOf(forward)) {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", 18222), 5_000)
                socket.soTimeout = 5_000
                assertEquals("SSH-", String(socket.getInputStream().readNBytes(4)))
            }
        }
    }

    @Test
    fun socks5ForwardReachesTheServerThroughTheTunnel() {
        val forward = PortForwardSpec(ForwardTypes.DYNAMIC, 11080, "", 0)
        withConnection(listOf(forward)) {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", 11080), 5_000)
                socket.soTimeout = 5_000
                val out = socket.getOutputStream()
                val input = socket.getInputStream()
                out.write(byteArrayOf(5, 1, 0))
                assertEquals(listOf<Byte>(5, 0), input.readNBytes(2).toList())
                val port = serverPort()
                out.write(byteArrayOf(5, 1, 0, 1, 127, 0, 0, 1, (port shr 8).toByte(), port.toByte()))
                assertEquals(0.toByte(), input.readNBytes(10)[1])
                assertEquals("SSH-", String(input.readNBytes(4)))
            }
        }
    }

    @Test
    fun secondShellSharesTheFirstConnection() {
        val key = File(arguments.getString("ed25519Key")!!).readText()
        val first = connect(key)
        try {
            val second = SshConnectionManager(context, config(key), first) {}
            second.connect()
            try {
                assertTrue(second.isConnected)
                first.disconnect()
                second.write("echo SHARED_$((5*5))\n".toByteArray())
                waitFor("shared shell output") { screen(second).contains("SHARED_25") }
            } finally {
                second.disconnect()
            }
        } finally {
            first.disconnect()
        }
    }

    @Test
    fun jumpHostReachesTheServerThroughAnotherConnection() {
        val key = File(arguments.getString("ed25519Key")!!).readText()
        val target = config(key).copy(host = "127.0.0.1", jumpHosts = listOf(config(key)))
        val manager = connect(target)
        try {
            assertTrue(manager.isConnected)
            manager.write("echo JUMPED_$((3*3))\n".toByteArray())
            waitFor("jump host shell output") { screen(manager).contains("JUMPED_9") }
        } finally {
            manager.disconnect()
        }
    }

    private fun serverPort() = arguments.getString("port")!!.toInt()

    private fun withConnection(forwards: List<PortForwardSpec>, body: () -> Unit) {
        val manager = connect(File(arguments.getString("ed25519Key")!!).readText(), forwards)
        try { body() } finally { manager.disconnect() }
    }

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

    private fun config(key: String, forwards: List<PortForwardSpec> = emptyList()) = SshConnectionConfig(
        host = arguments.getString("host")!!,
        port = serverPort(),
        username = arguments.getString("user")!!,
        authType = AuthTypes.PUBLIC_KEY,
        password = null, keyPath = null, keyMaterial = key, keyPassphrase = null,
        keepAliveSeconds = 30, forwards = forwards
    )

    private fun connect(key: String, forwards: List<PortForwardSpec> = emptyList()) =
        connect(config(key, forwards))

    private fun connect(config: SshConnectionConfig): SshConnectionManager {
        val exited = CountDownLatch(1)
        repeat(4) { attempt ->
            val manager = SshConnectionManager(context, config) { exited.countDown() }
            try {
                manager.connect()
                return manager
            } catch (e: Throwable) {
                val review = generateSequence(e) { it.cause }.filterIsInstance<HostKeyReviewRequired>().firstOrNull()
                if (review == null || attempt == 3) throw e
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
