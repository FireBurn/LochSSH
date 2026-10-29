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

    @Test
    fun tmuxScrollbackIsReachableAndLeavingReturnsToTheBottom() {
        val key = File(arguments.getString("ed25519Key")!!).readText()
        val manager = connect(config(key), "tmux new-session -s lochscroll")
        try {
            waitFor("tmux shell") { screen(manager).isNotBlank() }
            manager.write("for i in \$(seq 1 300); do echo scrollline_\$i; done\n".toByteArray())
            waitFor("last line") { screen(manager).contains("scrollline_300") }
            assertTrue(manager.scrollMultiplexer(true, 40))
            waitFor("older lines") { !screen(manager).contains("scrollline_300") && screen(manager).contains("scrollline_2") }
            assertTrue(manager.multiplexerScrolled.value)
            // Any key leaves scroll mode and is then passed on, so use the call the Live button makes.
            manager.leaveMultiplexerScroll()
            waitFor("back at the bottom") { !manager.multiplexerScrolled.value }
            manager.write("tmux kill-session -t lochscroll\n".toByteArray())
            // The write is queued, so give it time to leave before the disconnect cancels it.
            Thread.sleep(1_500)
        } finally {
            manager.disconnect()
        }
    }

    // The hybrid key exchange uses the platform X25519 for one half and BouncyCastle for ML-KEM.
    @Test
    fun hybridMlKemKeyExchangeWorksAlongsideThePlatformX25519() {
        assumeTrue(arguments.getString("mlkem") == "true")
        val key = File(arguments.getString("ed25519Key")!!).readText()
        val jsch = com.jcraft.jsch.JSch()
        jsch.setHostKeyRepository(HostKeyTrust.repository(context))
        IdentityLoader.add(jsch, null, key, null)
        repeat(2) { attempt ->
            val session = jsch.getSession(arguments.getString("user"), arguments.getString("host"), serverPort())
            session.setConfig("StrictHostKeyChecking", "yes")
            session.setConfig("kex", "mlkem768x25519-sha256")
            try {
                session.connect(10_000)
                assertTrue(session.isConnected)
                session.disconnect()
                return
            } catch (e: Throwable) {
                val review = generateSequence(e) { it.cause }.filterIsInstance<HostKeyReviewRequired>().firstOrNull()
                if (review == null || attempt == 1) throw e
                HostKeyTrust.accept(context, review.key)
            }
        }
    }

    // Signs in over SSH only to start mosh-server, then runs a command over UDP.
    @Test
    fun moshSessionRunsACommandOverUdp() {
        assumeTrue(arguments.getString("mosh") == "true")
        val key = File(arguments.getString("ed25519Key")!!).readText()
        val manager = connect(config(key).copy(mosh = true))
        try {
            assertTrue(manager.isConnected)
            manager.write("echo MOSH_$((6*7))\r".toByteArray())
            waitFor("mosh screen output") { screen(manager).contains("MOSH_42") }
            manager.terminal.buffer.resize(100, 30)
            manager.resize(100, 30)
            manager.write("echo SIZE_$(stty size | tr ' ' x)\r".toByteArray())
            try { waitFor("size change") { screen(manager).contains("SIZE_30x100") } }
            catch (e: AssertionError) { throw AssertionError("screen was:\n" + screen(manager).lines().filter { it.isNotBlank() }.joinToString("\n"), e) }
        } finally {
            manager.disconnect()
        }
    }

    @Test
    fun theConnectionNegotiatesCompressionAndStillWorksWithoutIt() {
        val key = File(arguments.getString("ed25519Key")!!).readText()
        for ((compress, expected) in listOf(true to "compression: zlib@openssh.com", false to "compression: none")) {
            val lines = java.util.Collections.synchronizedList(ArrayList<String>())
            val jsch = com.jcraft.jsch.JSch()
            jsch.setHostKeyRepository(HostKeyTrust.repository(context))
            jsch.setInstanceLogger(object : com.jcraft.jsch.Logger {
                override fun isEnabled(level: Int) = true
                override fun log(level: Int, message: String) { lines.add(message) }
            })
            IdentityLoader.add(jsch, null, key, null)
            val session = jsch.getSession(arguments.getString("user"), arguments.getString("host"), serverPort())
            session.setConfig("StrictHostKeyChecking", "yes")
            if (compress) {
                session.setConfig("compression.s2c", "zlib@openssh.com,none")
                session.setConfig("compression.c2s", "zlib@openssh.com,none")
            }
            try { session.connect(10_000) } catch (e: Throwable) {
                val review = generateSequence(e) { it.cause }.filterIsInstance<HostKeyReviewRequired>().firstOrNull() ?: throw e
                HostKeyTrust.accept(context, review.key)
                session.disconnect()
                val again = jsch.getSession(arguments.getString("user"), arguments.getString("host"), serverPort())
                again.setConfig("StrictHostKeyChecking", "yes")
                if (compress) {
                    again.setConfig("compression.s2c", "zlib@openssh.com,none")
                    again.setConfig("compression.c2s", "zlib@openssh.com,none")
                }
                again.connect(10_000)
                try { assertTrue(lines.joinToString("\n"), lines.any { it.contains(expected) }) } finally { again.disconnect() }
                continue
            }
            try { assertTrue(lines.joinToString("\n"), lines.any { it.contains(expected) }) } finally { session.disconnect() }
        }
    }

    @Test
    fun oneCommandScansTheNamesOfTmuxSessions() {
        assumeTrue(arguments.getString("tmuxSession") != null)
        val key = File(arguments.getString("ed25519Key")!!).readText()
        val manager = connect(config(key))
        try {
            val names = manager.scanTmuxNames()
            assertTrue(names.toString(), names.any { it.sessionName == arguments.getString("tmuxSession") })
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
            assertTrue(sessions.toString(), sessions.sessions.isNotEmpty())

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

    private fun connect(config: SshConnectionConfig, command: String? = null): SshConnectionManager {
        val exited = CountDownLatch(1)
        repeat(4) { attempt ->
            val manager = SshConnectionManager(context, config, null, command) { exited.countDown() }
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
