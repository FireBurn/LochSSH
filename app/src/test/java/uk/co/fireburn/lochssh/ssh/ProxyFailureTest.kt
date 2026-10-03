package uk.co.fireburn.lochssh.ssh

import com.jcraft.jsch.JSchException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyFailureTest {
    @Test fun timesOutOnASilentHttpProxy() = timeout(SshProxyType.HTTP)
    @Test fun timesOutOnASilentSocksProxy() = timeout(SshProxyType.SOCKS5)

    @Test fun refusesToConnectAfterBeingClosed() {
        for (type in SshProxyType.entries) {
            val proxy = SshProxySettings(type, "127.0.0.1", 1).create()
            proxy.close()
            val failure = assertThrows(JSchException::class.java) { proxy.connect(null, "server.invalid", 22, 1000) }
            assertTrue(generateSequence<Throwable>(failure) { it.cause }.any { it is java.io.InterruptedIOException })
        }
    }

    private fun timeout(type: SshProxyType) {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 3000
            val proxy = SshProxySettings(type, "127.0.0.1", server.localPort).create()
            val finished = CountDownLatch(1)
            val failure = AtomicReference<Throwable>()
            val worker = thread(isDaemon = true) {
                try { proxy.connect(null, "server.invalid", 22, 300) }
                catch (error: Throwable) { failure.set(error) }
                finally { finished.countDown() }
            }
            try {
                server.accept().use { client ->
                    client.soTimeout = 3000
                    assertTrue(client.getInputStream().read() >= 0)
                    assertTrue("Proxy did not honour its read timeout", finished.await(2, TimeUnit.SECONDS))
                    assertTrue(generateSequence(failure.get()) { it.cause }.any { it is SocketTimeoutException })
                    assertTrue(client.getInputStream().readBytes().isNotEmpty())
                }
            } finally { proxy.close(); worker.join(3000) }
            assertFalse(worker.isAlive)
        }
    }
}
