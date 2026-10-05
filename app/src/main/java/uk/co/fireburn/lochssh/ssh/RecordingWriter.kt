package uk.co.fireburn.lochssh.ssh

import java.io.OutputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class RecordingWriter(
    private val output: OutputStream,
    private val sizeLimit: Long = 64L * 1024 * 1024,
    queueCapacity: Int = 16
) {
    data class State(val accepting: Boolean = true, val closed: Boolean = false,
        val bytesWritten: Long = 0, val error: String? = null)

    private val queue = ArrayBlockingQueue<ByteArray>(queueCapacity)
    private val lock = Any()
    private val finished = CountDownLatch(1)
    private val current = MutableStateFlow(State())
    val state = current.asStateFlow()
    private var acceptedBytes = 0L

    init {
        require(sizeLimit > 0)
        thread(name = "terminal-recording", isDaemon = true) { writeQueued() }
    }

    fun append(bytes: ByteArray, length: Int): Boolean = synchronized(lock) {
        require(length in 0..bytes.size)
        if (!current.value.accepting) return false
        if (length == 0) return true
        if (length > 64 * 1024 || length.toLong() > sizeLimit - acceptedBytes) {
            stopWithError("Recording size limit reached")
            return false
        }
        if (!queue.offer(bytes.copyOf(length))) {
            stopWithError("Recording stopped: storage could not keep up")
            return false
        }
        acceptedBytes += length
        true
    }

    fun stop() = synchronized(lock) {
        current.value = current.value.copy(accepting = false)
    }

    fun awaitClosed(timeoutMillis: Long): Boolean = finished.await(timeoutMillis, TimeUnit.MILLISECONDS)

    private fun stopWithError(message: String) {
        current.value = current.value.copy(accepting = false, error = current.value.error ?: message)
    }

    private fun writeQueued() {
        try {
            while (true) {
                val bytes = queue.poll(50, TimeUnit.MILLISECONDS)
                if (bytes != null) {
                    output.write(bytes)
                    synchronized(lock) {
                        current.value = current.value.copy(bytesWritten = current.value.bytesWritten + bytes.size)
                    }
                } else if (synchronized(lock) { !current.value.accepting && queue.isEmpty() }) break
            }
        } catch (_: Exception) {
            synchronized(lock) { stopWithError("Could not write recording") }
            queue.clear()
        } finally {
            try { output.close() }
            catch (_: Exception) { synchronized(lock) { stopWithError("Could not close recording") } }
            synchronized(lock) { current.value = current.value.copy(accepting = false, closed = true) }
            finished.countDown()
        }
    }
}
