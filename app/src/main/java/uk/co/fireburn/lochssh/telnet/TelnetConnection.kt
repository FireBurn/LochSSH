package uk.co.fireburn.lochssh.telnet

import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

// Presents a Telnet socket as plain text streams: option commands are answered here and
// never reach the terminal, and outgoing 0xFF bytes are doubled.
class TelnetConnection(private val socket: Socket, cols: Int, rows: Int) {
    private val negotiator = TelnetNegotiator()
    private val writeLock = Any()
    private val raw = ByteArray(4096)
    private var pending = ByteArray(0)
    private var pendingOffset = 0

    val isOpen: Boolean get() = socket.isConnected && !socket.isClosed

    init {
        synchronized(negotiator) { negotiator.windowSize(cols, rows) }
    }

    val input: InputStream = object : InputStream() {
        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            while (pendingOffset >= pending.size) {
                val count = socket.getInputStream().read(raw)
                if (count < 0) return -1
                val result = synchronized(negotiator) { negotiator.receive(raw, count) }
                if (result.replies.isNotEmpty()) send(result.replies)
                pending = result.text
                pendingOffset = 0
            }
            val size = minOf(length, pending.size - pendingOffset)
            System.arraycopy(pending, pendingOffset, buffer, offset, size)
            pendingOffset += size
            return size
        }

        override fun close() = socket.close()
    }

    val output: OutputStream = object : OutputStream() {
        override fun write(b: Int) = send(TelnetNegotiator.escape(byteArrayOf(b.toByte())))
        override fun write(b: ByteArray, off: Int, len: Int) =
            send(TelnetNegotiator.escape(b.copyOfRange(off, off + len)))
        override fun close() = socket.close()
    }

    fun resize(cols: Int, rows: Int) {
        val message = synchronized(negotiator) { negotiator.windowSize(cols, rows) }
        if (message.isNotEmpty()) send(message)
    }

    fun close() = runCatching { socket.close() }

    private fun send(bytes: ByteArray) {
        synchronized(writeLock) {
            socket.getOutputStream().write(bytes)
            socket.getOutputStream().flush()
        }
    }
}
