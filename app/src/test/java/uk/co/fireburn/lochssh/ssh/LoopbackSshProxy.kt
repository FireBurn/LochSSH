package uk.co.fireburn.lochssh.ssh

import java.io.DataInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

internal class LoopbackSshProxy(
    private val type: SshProxyType,
    private val destination: String,
    private val authenticated: Boolean = false,
    private val relay: Boolean = true
) : AutoCloseable {
    private val listener = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
    private val sockets = mutableListOf<Socket>()
    private val request = CompletableFuture<String>()
    val port: Int get() = listener.localPort
    private val worker = thread(isDaemon = true, name = "test-ssh-proxy") {
        try {
            val client = listener.accept().also(::track)
            client.soTimeout = 5_000
            val target = when (type) {
                SshProxyType.HTTP -> readHttp(client)
                SshProxyType.SOCKS5 -> readSocks(client)
            }
            if (target == null) { request.complete("rejected"); return@thread }
            request.complete(target)
            if (relay) {
                check(target == "$destination:22") { "Unexpected proxy destination" }
                val server = Socket().also(::track)
                server.connect(java.net.InetSocketAddress(destination, 22), 5_000)
                server.soTimeout = 5_000
                thread(isDaemon = true, name = "test-proxy-reply") {
                    try { server.getInputStream().copyTo(client.getOutputStream()) }
                    catch (_: java.io.IOException) { }
                }
                client.getInputStream().copyTo(server.getOutputStream())
            }
        } catch (e: Exception) { request.completeExceptionally(e) }
    }

    fun target(): String = request.get(5, TimeUnit.SECONDS)

    private fun readHttp(client: Socket): String? {
        val input = DataInputStream(client.getInputStream())
        val first = line(input)
        val headers = mutableListOf<String>()
        while (true) {
            val header = line(input)
            if (header.isEmpty()) break
            check(headers.size < 20)
            headers += header
        }
        val credentials = Base64.getEncoder().encodeToString("proxy-user:proxy-password".toByteArray())
        val accepted = !authenticated || headers.contains("Proxy-Authorization: Basic $credentials")
        client.getOutputStream().apply {
            write((if (accepted) "HTTP/1.0 200 Connected\r\n\r\n" else "HTTP/1.0 407 Authentication required\r\n\r\n").toByteArray())
            flush()
        }
        return if (accepted) first.split(' ')[1] else null
    }

    private fun line(input: DataInputStream): String {
        val result = StringBuilder()
        while (result.length < 8192) {
            val next = input.readUnsignedByte()
            if (next == 10) return result.toString().removeSuffix("\r")
            result.append(next.toChar())
        }
        error("Proxy header is too large")
    }

    private fun readSocks(client: Socket): String? {
        val input = DataInputStream(client.getInputStream())
        val output = client.getOutputStream()
        check(input.readUnsignedByte() == 5)
        val methods = ByteArray(input.readUnsignedByte()).also(input::readFully)
        val method = if (authenticated) 2 else 0
        if (methods.none { it.toInt() == method }) {
            output.write(byteArrayOf(5, -1)); output.flush(); return null
        }
        output.write(byteArrayOf(5, method.toByte())); output.flush()
        if (authenticated) {
            check(input.readUnsignedByte() == 1)
            val user = text(input)
            val password = text(input)
            val accepted = user == "proxy-user" && password == "proxy-password"
            output.write(byteArrayOf(1, if (accepted) 0 else 1)); output.flush()
            if (!accepted) return null
        }
        check(input.readUnsignedByte() == 5 && input.readUnsignedByte() == 1 && input.readUnsignedByte() == 0)
        val host = when (input.readUnsignedByte()) {
            1 -> InetAddress.getByAddress(ByteArray(4).also(input::readFully)).hostAddress
            3 -> text(input)
            4 -> InetAddress.getByAddress(ByteArray(16).also(input::readFully)).hostAddress
            else -> error("Unexpected address type")
        }
        val port = input.readUnsignedShort()
        Socks5Protocol.reply(output, 0)
        return "$host:$port"
    }

    private fun text(input: DataInputStream): String =
        ByteArray(input.readUnsignedByte()).also(input::readFully).toString(Charsets.UTF_8)

    private fun track(socket: Socket) { synchronized(sockets) { sockets += socket } }

    override fun close() {
        listener.close()
        synchronized(sockets) { sockets.forEach { it.close() } }
        worker.join(5_000)
        check(!worker.isAlive) { "Test proxy did not stop" }
    }
}
