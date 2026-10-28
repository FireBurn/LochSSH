package uk.co.fireburn.lochssh.local

import android.os.Process
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream

// Runs the device shell over pipes. Input goes through LineDiscipline, output has its
// line ends changed to CR LF, and interrupts go to the shell's children by signal.
class LocalShellConnection(directory: File) {
    private val process = ProcessBuilder("/system/bin/sh", "-c", "echo \$\$; exec /system/bin/sh -i 2>/dev/null")
        .directory(directory)
        .redirectErrorStream(true)
        .apply { environment()["TERM"] = "xterm-256color" }
        .start()
    private val discipline = LineDiscipline()
    private val shellInput = process.outputStream
    private val pending = ByteArrayOutputStream()
    private val echoed = java.util.concurrent.LinkedBlockingQueue<ByteArray>()
    private var lastWasCarriageReturn = false
    private val pid: Int = readPid(process.inputStream)

    init {
        // The shell warns that it has no terminal before it reads any input, so stderr starts
        // closed and is joined to the output once it is running.
        shellInput.write("exec 2>&1\n".toByteArray())
        shellInput.flush()
    }

    val isOpen: Boolean get() = process.isAlive

    val input: InputStream = object : InputStream() {
        private var chunk = ByteArray(0)
        private var offset = 0
        private val raw = ByteArray(4096)

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(buffer: ByteArray, off: Int, length: Int): Int {
            if (length == 0) return 0
            while (offset >= chunk.size) {
                val echo = echoed.poll()
                if (echo != null) { chunk = echo; offset = 0; continue }
                val waiting = try { process.inputStream.available() } catch (_: java.io.IOException) { return -1 }
                if (waiting == 0) {
                    if (!process.isAlive) return -1
                    Thread.sleep(POLL_MS)
                    continue
                }
                val count = process.inputStream.read(raw)
                if (count < 0) return -1
                chunk = withCarriageReturns(raw, count)
                offset = 0
            }
            val size = minOf(length, chunk.size - offset)
            System.arraycopy(chunk, offset, buffer, off, size)
            offset += size
            return size
        }

        override fun close() = this@LocalShellConnection.close()
    }

    val output: OutputStream = object : OutputStream() {
        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            val result = discipline.feed(b.copyOfRange(off, off + len))
            if (result.echo.isNotEmpty()) echoed.add(result.echo)
            if (result.toShell.isNotEmpty()) {
                shellInput.write(result.toShell)
                shellInput.flush()
            }
            if (result.interrupt) signalChildren(SIGINT)
            if (result.endOfInput) runCatching { shellInput.close() }
        }

        override fun close() = this@LocalShellConnection.close()
    }

    fun resize(cols: Int, rows: Int) = Unit

    fun close() {
        if (process.isAlive) signalChildren(SIGTERM)
        runCatching { shellInput.close() }
        process.destroy()
    }

    // The shell does not pass the signal on to a running command, so it is sent to each
    // descendant read from /proc.
    private fun signalChildren(signal: Int) {
        val parents = HashMap<Int, Int>()
        File("/proc").listFiles()?.forEach { entry ->
            val id = entry.name.toIntOrNull() ?: return@forEach
            val stat = runCatching { File(entry, "stat").readText() }.getOrNull() ?: return@forEach
            val parent = stat.substringAfterLast(')').trim().split(' ').getOrNull(1)?.toIntOrNull()
            if (parent != null) parents[id] = parent
        }
        val targets = ArrayDeque<Int>().apply { add(pid) }
        val found = mutableListOf<Int>()
        while (targets.isNotEmpty()) {
            val current = targets.removeFirst()
            parents.filterValues { it == current }.keys.forEach { found.add(it); targets.add(it) }
        }
        found.asReversed().forEach { runCatching { Process.sendSignal(it, signal) } }
    }

    private fun withCarriageReturns(data: ByteArray, count: Int): ByteArray {
        pending.reset()
        for (i in 0 until count) {
            if (data[i] == LF && !lastWasCarriageReturn) pending.write(CR.toInt())
            pending.write(data[i].toInt())
            lastWasCarriageReturn = data[i] == CR
        }
        return pending.toByteArray()
    }

    private fun readPid(stream: InputStream): Int {
        val digits = StringBuilder()
        while (true) {
            val next = stream.read()
            if (next < 0 || next == '\n'.code) break
            digits.append(next.toChar())
        }
        return digits.toString().trim().toInt()
    }

    private companion object {
        const val CR = '\r'.code.toByte()
        const val LF = '\n'.code.toByte()
        const val SIGINT = 2
        const val SIGTERM = 15
        const val POLL_MS = 20L
    }
}
