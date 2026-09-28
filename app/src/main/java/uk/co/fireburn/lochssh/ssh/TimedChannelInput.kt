package uk.co.fireburn.lochssh.ssh

import java.io.InputStream
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

internal class TimedChannelInput(private val input: InputStream, private val isOpen: () -> Boolean) : InputStream() {
    @Volatile var timeoutMillis: Int = 0

    override fun read(): Int {
        val bytes = ByteArray(1)
        return if (read(bytes, 0, 1) < 0) -1 else bytes[0].toInt() and 255
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        require(offset >= 0 && length >= 0 && offset <= bytes.size - length)
        if (length == 0) return 0
        val started = System.nanoTime()
        while (true) {
            val available = input.available()
            if (available > 0) return input.read(bytes, offset, minOf(length, available))
            if (!isOpen()) return -1
            val timeout = timeoutMillis
            if (timeout > 0 && System.nanoTime() - started >= timeout.toLong() * 1_000_000L) {
                throw SocketTimeoutException("Jump host read timed out")
            }
            try { Thread.sleep(5) } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedIOException("Jump host read cancelled").apply { initCause(error) }
            }
        }
    }

    override fun available(): Int = input.available()
    override fun close() = input.close()
}
