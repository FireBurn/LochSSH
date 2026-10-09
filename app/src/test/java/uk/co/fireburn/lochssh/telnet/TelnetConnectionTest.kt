package uk.co.fireburn.lochssh.telnet

import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class TelnetConnectionTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun textReachesTheReaderAndOptionRepliesReachTheServer() {
        ServerSocket(0).use { server ->
            val serverSaw = java.io.ByteArrayOutputStream()
            val done = thread {
                server.accept().use { client ->
                    client.getOutputStream().write(bytes(255, 253, 31, 'h'.code, 'i'.code, 255, 255, '\n'.code))
                    client.getOutputStream().flush()
                    val buffer = ByteArray(64)
                    val count = client.getInputStream().read(buffer)
                    serverSaw.write(buffer, 0, count)
                    client.soTimeout = 300
                    try { while (true) { val n = client.getInputStream().read(buffer); if (n < 0) break; serverSaw.write(buffer, 0, n) } }
                    catch (_: java.net.SocketTimeoutException) { }
                }
            }
            val socket = Socket("127.0.0.1", server.localPort)
            val connection = TelnetConnection(socket, 100, 40)
            val text = ByteArray(16)
            var total = 0
            while (total < 4) total += connection.input.read(text, total, text.size - total)
            assertArrayEquals(bytes('h'.code, 'i'.code, 255, '\n'.code), text.copyOf(4))
            connection.output.write(bytes(1, 255, 2))
            connection.resize(120, 30)
            Thread.sleep(400)
            connection.close()
            done.join(3000)
            val seen = serverSaw.toByteArray().toList()
            val will = bytes(255, 251, 31, 255, 250, 31, 0, 100, 0, 40, 255, 240).toList()
            assertEquals(will, seen.take(will.size))
            val rest = seen.drop(will.size)
            assertEquals(bytes(1, 255, 255, 2).toList(), rest.take(4))
            assertEquals(bytes(255, 250, 31, 0, 120, 0, 30, 255, 240).toList(), rest.drop(4))
        }
    }

    @Test
    fun closedServerEndsTheStream() {
        ServerSocket(0).use { server ->
            thread { server.accept().close() }
            val connection = TelnetConnection(Socket("127.0.0.1", server.localPort), 80, 24)
            assertEquals(-1, connection.input.read(ByteArray(8), 0, 8))
        }
    }
}
