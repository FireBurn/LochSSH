package uk.co.fireburn.lochssh.serial

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

// The part of a serial port the session needs, so everything but the USB hardware can be tested.
interface SerialIo {
    // Returns the bytes read, 0 when nothing arrived in time. Throws when the adapter is gone.
    fun read(buffer: ByteArray, timeoutMs: Int): Int
    fun write(data: ByteArray)
    fun close()
}

class SerialConnection(private val io: SerialIo) {
    @Volatile private var closed = false

    val isOpen: Boolean get() = !closed

    val input: InputStream = object : InputStream() {
        private val raw = ByteArray(4096)

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(buffer: ByteArray, off: Int, length: Int): Int {
            if (length == 0) return 0
            while (!closed) {
                val count = try { io.read(raw, READ_TIMEOUT_MS) } catch (e: IOException) {
                    if (closed) return -1
                    closed = true
                    runCatching { io.close() }
                    throw IOException("The USB serial adapter was disconnected", e)
                }
                if (count > 0) {
                    val size = minOf(count, length)
                    System.arraycopy(raw, 0, buffer, off, size)
                    return size
                }
            }
            return -1
        }

        override fun close() = this@SerialConnection.close()
    }

    val output: OutputStream = object : OutputStream() {
        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (closed) throw IOException("The serial connection is closed")
            io.write(b.copyOfRange(off, off + len))
        }

        override fun close() = this@SerialConnection.close()
    }

    fun close() {
        if (closed) return
        closed = true
        runCatching { io.close() }
    }

    private companion object {
        const val READ_TIMEOUT_MS = 200
    }
}
