package uk.co.fireburn.lochssh.ssh

import com.jcraft.jsch.JSch
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectingSessionsTest {
    @Test
    fun closesDirectHandshakeBeforeItsReadTimeout() {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 3000
            val session = JSch().getSession("test", "127.0.0.1", server.localPort)
            session.setConfig("StrictHostKeyChecking", "yes")
            val sessions = ConnectingSessions()
            val control = ConnectionAttemptControl()
            val finished = CountDownLatch(1)
            val failure = AtomicReference<Throwable>()
            val worker = thread(isDaemon = true) {
                try { control.run { sessions.add(session); session.connect(10_000) } }
                catch (error: Throwable) { failure.set(error) }
                finally { finished.countDown() }
            }
            try {
                server.accept().use {
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
                    while (!session.isConnected && System.nanoTime() < deadline) Thread.sleep(5)
                    assertTrue(session.isConnected)
                    control.cancel()
                    sessions.close()
                    assertTrue(finished.await(2, TimeUnit.SECONDS))
                    assertNotNull(failure.get())
                    assertFalse(session.isConnected)
                }
            } finally {
                control.cancel()
                sessions.close()
                session.disconnect()
                worker.join(3000)
            }
            assertFalse(worker.isAlive)
        }
    }
}
