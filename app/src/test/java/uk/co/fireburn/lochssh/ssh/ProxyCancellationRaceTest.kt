package uk.co.fireburn.lochssh.ssh

import com.jcraft.jsch.JSch
import com.jcraft.jsch.Proxy
import com.jcraft.jsch.SocketFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InterruptedIOException
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyCancellationRaceTest {
    @Test fun closesASocketReturnedAfterCancellation() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val created = Socket()
        val factory = object : SocketFactory {
            override fun createSocket(host: String, port: Int): Socket {
                entered.countDown()
                check(release.await(3, TimeUnit.SECONDS))
                return created
            }
            override fun getInputStream(socket: Socket) = socket.getInputStream()
            override fun getOutputStream(socket: Socket) = socket.getOutputStream()
        }
        val delegate = object : Proxy {
            override fun connect(factory: SocketFactory?, host: String, port: Int, timeout: Int) {
                requireNotNull(factory).createSocket(host, port)
            }
            override fun getInputStream() = ByteArrayInputStream(byteArrayOf())
            override fun getOutputStream() = ByteArrayOutputStream()
            override fun getSocket() = created
            override fun close() { created.close() }
        }
        val proxy = CancellableSshProxy(delegate)
        val failure = AtomicReference<Throwable>()
        val worker = thread(isDaemon = true) {
            try { proxy.connect(factory, "localhost", 22, 1000) }
            catch (error: Throwable) { failure.set(error) }
        }
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            proxy.close()
            release.countDown()
            worker.join(3000)
            assertFalse(worker.isAlive)
            assertTrue(created.isClosed)
            assertTrue(failure.get() is InterruptedIOException)
        } finally { release.countDown(); proxy.close(); created.close(); worker.join(3000) }
    }

    @Test fun rejectsSessionsRegisteredAfterCancellation() {
        val sessions = ConnectingSessions()
        sessions.close()
        val socket = Socket()
        val proxy = object : Proxy {
            override fun connect(factory: SocketFactory?, host: String, port: Int, timeout: Int) = Unit
            override fun getInputStream() = ByteArrayInputStream(byteArrayOf())
            override fun getOutputStream() = ByteArrayOutputStream()
            override fun getSocket() = socket
            override fun close() { socket.close() }
        }
        try {
            val session = JSch().getSession("user", "localhost", 22)
            assertThrows(InterruptedIOException::class.java) { sessions.add(session, proxy) }
            assertTrue(socket.isClosed)
        } finally { socket.close() }
    }
}
