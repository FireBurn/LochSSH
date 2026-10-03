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

class ProxyCancellationTest {
    @Test fun cancelsASilentHttpHandshake() = cancel(SshProxyType.HTTP)
    @Test fun cancelsASilentSocksHandshake() = cancel(SshProxyType.SOCKS5)

    private fun cancel(type: SshProxyType) {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 3000
            val proxy = SshProxySettings(type, "127.0.0.1", server.localPort).create()
            val session = JSch().getSession("test", "destination.invalid", 22)
            session.setProxy(proxy)
            val sessions = ConnectingSessions()
            val control = ConnectionAttemptControl()
            val finished = CountDownLatch(1)
            val failure = AtomicReference<Throwable>()
            sessions.add(session, proxy)
            val worker = thread(isDaemon = true) {
                try { control.run { session.connect(10_000) } }
                catch (error: Throwable) { failure.set(error) }
                finally { finished.countDown() }
            }
            try {
                server.accept().use { client ->
                    client.soTimeout = 3000
                    assertTrue(client.getInputStream().read() >= 0)
                    assertFalse(session.isConnected)
                    control.cancel()
                    sessions.close()
                    assertTrue("Proxy handshake did not stop after cancellation", finished.await(2, TimeUnit.SECONDS))
                    assertNotNull(failure.get())
                    assertFalse(session.isConnected)
                }
            } finally {
                control.cancel()
                proxy.close()
                sessions.close()
                session.disconnect()
                worker.join(3000)
            }
            assertFalse(worker.isAlive)
        }
    }
}
