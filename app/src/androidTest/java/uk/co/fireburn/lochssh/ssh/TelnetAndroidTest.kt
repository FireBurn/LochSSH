package uk.co.fireburn.lochssh.ssh

import androidx.test.platform.app.InstrumentationRegistry
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelnetAndroidTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun showsServerTextSendsInputAndReportsTheServerClosing() {
        ServerSocket(0).use { server ->
            val received = java.io.ByteArrayOutputStream()
            val serverDone = CountDownLatch(1)
            thread {
                server.accept().use { client ->
                    val out = client.getOutputStream()
                    out.write(byteArrayOf(-1, -3, 31, -1, -5, 1))
                    out.write("login: ".toByteArray())
                    out.flush()
                    val buffer = ByteArray(128)
                    client.soTimeout = 5_000
                    var count = 0
                    while (!received.toString().contains("root\r")) {
                        count = client.getInputStream().read(buffer)
                        if (count < 0) break
                        received.write(buffer, 0, count)
                    }
                    out.write("Welcome\r\n".toByteArray())
                    out.flush()
                    Thread.sleep(300)
                }
                serverDone.countDown()
            }
            val exited = CountDownLatch(1)
            val config = SshConnectionConfig(
                host = "127.0.0.1", port = server.localPort, username = "", authType = "NONE",
                password = null, keyPath = null, keyMaterial = null, keyPassphrase = null,
                keepAliveSeconds = 0, telnet = true
            )
            val manager = SshConnectionManager(context, config) { exited.countDown() }
            manager.connect()
            assertTrue(manager.isConnected)
            waitFor { screen(manager).contains("login:") }
            manager.write("root\r".toByteArray())
            waitFor { screen(manager).contains("Welcome") }
            assertTrue(serverDone.await(5, TimeUnit.SECONDS))
            assertTrue(exited.await(5, TimeUnit.SECONDS))
            assertTrue(received.toString().contains("root\r"))
            // The size reply to the server's DO NAWS request must have been sent before the text.
            assertEquals(listOf(-1, -5, 31), received.toByteArray().take(3).map { it.toInt() })
            manager.disconnect()
        }
    }

    private fun screen(manager: SshConnectionManager): String {
        val buffer = manager.terminal.buffer
        return (0 until buffer.rows).joinToString("\n") { row ->
            (0 until buffer.cols).joinToString("") { buffer.cellAt(row, it).text }
        }
    }

    private fun waitFor(check: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            if (check()) return
            Thread.sleep(100)
        }
        assertTrue("timed out", check())
    }
}
