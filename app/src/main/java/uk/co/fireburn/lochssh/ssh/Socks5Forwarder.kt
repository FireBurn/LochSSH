package uk.co.fireburn.lochssh.ssh

import android.util.Log
import com.jcraft.jsch.ChannelDirectTCPIP
import com.jcraft.jsch.Session
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.Semaphore
import kotlin.concurrent.thread

internal class Socks5Forwarder(private val session: Session, port: Int) {
    private val server = ServerSocket(port, 16, InetAddress.getByName("127.0.0.1"))
    val listenPort: Int get() = server.localPort
    private val slots = Semaphore(16)
    private val clients = ConcurrentHashMap.newKeySet<Socket>()
    private val channels = ConcurrentHashMap.newKeySet<ChannelDirectTCPIP>()
    private val workers = Executors.newFixedThreadPool(16) { task ->
        Thread(task, "socks5-client").apply { isDaemon = true }
    }
    @Volatile private var running = true

    fun start() {
        thread(name = "socks5-listener", isDaemon = true) {
            while (running) {
                val socket = try {
                    server.accept()
                } catch (_: SocketException) {
                    break
                }
                if (!slots.tryAcquire()) {
                    socket.close()
                    continue
                }
                clients += socket
                try {
                    workers.execute {
                        try {
                            serve(socket)
                        } finally {
                            clients -= socket
                            slots.release()
                        }
                    }
                } catch (_: RejectedExecutionException) {
                    clients -= socket
                    slots.release()
                    socket.close()
                }
            }
        }
    }

    fun stop() {
        running = false
        server.close()
        clients.forEach { runCatching { it.close() } }
        channels.forEach { runCatching { it.disconnect() } }
        workers.shutdownNow()
    }

    private fun serve(socket: Socket) {
        var channel: ChannelDirectTCPIP? = null
        try {
            socket.soTimeout = 15_000
            val request = Socks5Protocol.readRequest(socket.getInputStream(), socket.getOutputStream())
                ?: return
            channel = session.openChannel("direct-tcpip") as ChannelDirectTCPIP
            channels += channel
            channel.setHost(request.host)
            channel.setPort(request.port)
            channel.setOrgIPAddress(socket.inetAddress.hostAddress)
            channel.setOrgPort(socket.port)
            val remoteInput = channel.inputStream
            channel.connect(15_000)
            val remoteOutput = channel.outputStream
            Socks5Protocol.reply(socket.getOutputStream(), 0)
            socket.soTimeout = 0

            val response = thread(name = "socks5-response", isDaemon = true) {
                try {
                    remoteInput.copyTo(socket.getOutputStream())
                } catch (_: Exception) {
                } finally {
                    runCatching { socket.close() }
                }
            }
            try {
                socket.getInputStream().copyTo(remoteOutput)
            } finally {
                runCatching { remoteOutput.close() }
                response.join(15_000)
            }
        } catch (e: Exception) {
            Log.w("LochSSH", "SOCKS5 connection failed", e)
            if (channel?.isConnected != true) {
                runCatching { Socks5Protocol.reply(socket.getOutputStream(), 5) }
            }
        } finally {
            channel?.let { channels -= it; it.disconnect() }
            runCatching { socket.close() }
        }
    }
}
