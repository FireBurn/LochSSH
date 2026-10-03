package uk.co.fireburn.lochssh.ssh

import com.jcraft.jsch.Proxy
import com.jcraft.jsch.SocketFactory
import java.io.InterruptedIOException
import java.net.InetSocketAddress
import java.net.Socket

internal class CancellableSshProxy(private val proxy: Proxy) : Proxy by proxy {
    private val lock = Any()
    private var closed = false
    private var socket: Socket? = null

    override fun connect(factory: SocketFactory?, host: String, port: Int, timeout: Int) {
        val tracked = object : SocketFactory {
            override fun createSocket(host: String, port: Int): Socket {
                checkOpen()
                val created = factory?.createSocket(host, port) ?: Socket()
                synchronized(lock) {
                    if (closed) {
                        created.close()
                        throw InterruptedIOException("Proxy connection cancelled")
                    }
                    socket = created
                }
                if (factory == null) created.connect(InetSocketAddress(host, port), timeout.coerceAtLeast(0))
                checkOpen()
                return created
            }

            override fun getInputStream(socket: Socket) = factory?.getInputStream(socket) ?: socket.getInputStream()
            override fun getOutputStream(socket: Socket) = factory?.getOutputStream(socket) ?: socket.getOutputStream()
        }
        var connected = false
        try {
            proxy.connect(tracked, host, port, timeout)
            checkOpen()
            connected = true
        } finally { if (!connected) close() }
    }

    private fun checkOpen() {
        synchronized(lock) { if (closed) throw InterruptedIOException("Proxy connection cancelled") }
    }

    override fun close() {
        val pending = synchronized(lock) { closed = true; socket.also { socket = null } }
        pending?.close()
    }
}
