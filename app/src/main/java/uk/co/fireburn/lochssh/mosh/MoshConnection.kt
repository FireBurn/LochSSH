package uk.co.fireburn.lochssh.mosh

import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

// A Mosh session over UDP. The client sends numbered states of what the user did (keys and
// window size); the server sends the changes to the screen as terminal bytes. Both sides
// resend until acknowledged, so a lost packet or a changed network does not end the session.
class MoshConnection internal constructor(
    private val host: String,
    private val port: Int,
    key: ByteArray,
    columns: Int,
    rows: Int
) {
    private val crypto = MoshCrypto(key)
    private val lock = Object()
    private val events = ArrayList<ByteArray>()
    private var sentCount = 0
    private var ackedNum = 0L
    private var remoteNum = 0L
    private var sequence = 0L
    private var fragmentId = 0L
    private var lastSent = 0L
    private var ackWanted = 0L
    private var savedStamp = -1
    private var savedAt = 0L
    private val assembler = MoshFragments.Assembler()
    private val shown = LinkedBlockingQueue<ByteArray>()
    @Volatile private var socket: DatagramSocket? = null
    @Volatile private var closed = false

    val isOpen: Boolean get() = !closed

    init {
        events.add(MoshMessages.resize(columns, rows))
        openSocket()
        thread(name = "mosh-receive", isDaemon = true) { receiveLoop() }
        thread(name = "mosh-send", isDaemon = true) { sendLoop() }
    }

    val input: InputStream = object : InputStream() {
        private var chunk = ByteArray(0)
        private var offset = 0

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(buffer: ByteArray, off: Int, length: Int): Int {
            if (length == 0) return 0
            while (offset >= chunk.size) {
                chunk = shown.take()
                offset = 0
                if (chunk === END) { shown.put(END); return -1 }
            }
            val size = minOf(length, chunk.size - offset)
            System.arraycopy(chunk, offset, buffer, off, size)
            offset += size
            return size
        }

        override fun close() = this@MoshConnection.close()
    }

    val output: OutputStream = object : OutputStream() {
        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
        override fun write(b: ByteArray, off: Int, len: Int) = keys(b.copyOfRange(off, off + len))
        override fun close() = this@MoshConnection.close()
    }

    fun resize(columns: Int, rows: Int) {
        synchronized(lock) {
            events.add(MoshMessages.resize(columns, rows))
            lock.notifyAll()
        }
    }

    // Tells the server the user has left, so its shell ends instead of waiting for them.
    fun shutdown() {
        if (closed) return
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val bye = Instruction(ackedNum, Instruction.SHUTDOWN, remoteNum, ackedNum, ByteArray(0)).encode()
            repeat(SHUTDOWN_COPIES) { transmit(bye, now) }
        }
        close()
    }

    // Stands in for the phone changing networks: the socket breaks and a new one is opened.
    internal fun breakSocketForTest() {
        runCatching { socket?.close() }
    }

    fun close() {
        if (closed) return
        closed = true
        runCatching { socket?.close() }
        shown.put(END)
        synchronized(lock) { lock.notifyAll() }
    }

    // A failed log call must never end a network thread.
    private fun warn(message: String, error: Throwable) {
        runCatching { Log.w(TAG, message, error) }
    }

    private fun keys(bytes: ByteArray) {
        synchronized(lock) {
            // States already sent must not change, so only unsent keys are merged.
            val last = events.size - 1
            if (last >= sentCount && last >= 0 && isKeystroke(events[last])) {
                events[last] = MoshMessages.keystroke(keystrokeBytes(events[last]) + bytes)
            } else {
                events.add(MoshMessages.keystroke(bytes))
            }
            lock.notifyAll()
        }
    }

    private fun isKeystroke(event: ByteArray) = Protobuf.read(event).firstOrNull()?.number == 2

    private fun keystrokeBytes(event: ByteArray): ByteArray {
        val inner = Protobuf.read(event).first { it.number == 2 }
        return Protobuf.read(inner.data).first { it.number == 4 }.data
    }

    private fun openSocket() {
        val created = DatagramSocket()
        created.soTimeout = RECEIVE_TIMEOUT_MS
        created.connect(InetSocketAddress(host, port))
        socket = created
    }

    private fun receiveLoop() {
        val buffer = ByteArray(2048)
        while (!closed) {
            val current = socket
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                current?.receive(packet) ?: throw IOException("No socket")
                handle(buffer.copyOf(packet.length))
            } catch (_: java.net.SocketTimeoutException) {
                // Nothing arrived; the loop checks whether the session was closed.
            } catch (e: Exception) {
                if (closed) return
                warn("Mosh receive failed, opening a new socket", e)
                reopen()
            }
        }
    }

    private fun reopen() {
        while (!closed) {
            try {
                Thread.sleep(REOPEN_DELAY_MS)
                runCatching { socket?.close() }
                openSocket()
                return
            } catch (e: Exception) {
                warn("Could not open a Mosh socket yet", e)
            }
        }
    }

    private fun handle(packet: ByteArray) {
        val (nonce, plain) = crypto.decrypt(packet) ?: return
        if (nonce and MoshCrypto.TO_CLIENT == 0L || plain.size < 4) return
        val now = System.currentTimeMillis()
        synchronized(lock) {
            savedStamp = ((plain[0].toInt() and 0xFF) shl 8) or (plain[1].toInt() and 0xFF)
            savedAt = now
        }
        val complete = assembler.add(plain.copyOfRange(4, plain.size)) ?: return
        val instruction = Instruction.decode(complete) ?: return
        val chunks = ArrayList<ByteArray>()
        var finished = false
        synchronized(lock) {
            if (instruction.ackNum > ackedNum && instruction.ackNum <= events.size) ackedNum = instruction.ackNum
            when {
                instruction.newNum == Instruction.SHUTDOWN -> finished = true
                instruction.newNum == remoteNum -> ackWanted = now
                instruction.oldNum == remoteNum -> {
                    chunks.addAll(MoshMessages.hostBytes(instruction.diff))
                    remoteNum = instruction.newNum
                    ackWanted = now
                }
            }
            lock.notifyAll()
        }
        chunks.forEach { shown.put(it) }
        if (finished) close()
    }

    private fun sendLoop() {
        while (!closed) {
            synchronized(lock) {
                val now = System.currentTimeMillis()
                val unsent = sentCount < events.size
                val unacked = ackedNum < events.size
                val ack = ackWanted != 0L && now - ackWanted >= ACK_DELAY_MS
                if (unsent || ack || (unacked && now - lastSent >= RESEND_MS) || now - lastSent >= HEARTBEAT_MS) {
                    sendNow(now)
                }
                lock.wait(TICK_MS)
            }
        }
    }

    private fun sendNow(now: Long) {
        val from = ackedNum.toInt()
        val diff = MoshMessages.userMessage(events.subList(from, events.size))
        val instruction = Instruction(ackedNum, events.size.toLong(), remoteNum, ackedNum, diff).encode()
        transmit(instruction, now)
        sentCount = events.size
        lastSent = now
        ackWanted = 0L
    }

    private fun transmit(instruction: ByteArray, now: Long) {
        val target = socket ?: return
        val reply = if (savedStamp >= 0 && now - savedAt < 1000) (savedStamp + (now - savedAt).toInt()) and 0xFFFF else 0xFFFF
        for (fragment in MoshFragments.split(fragmentId++, instruction)) {
            val plain = ByteArray(4 + fragment.size)
            plain[0] = (now shr 8).toByte(); plain[1] = now.toByte()
            plain[2] = (reply shr 8).toByte(); plain[3] = reply.toByte()
            System.arraycopy(fragment, 0, plain, 4, fragment.size)
            val data = crypto.encrypt(sequence++, plain)
            try { target.send(DatagramPacket(data, data.size)) } catch (e: IOException) {
                warn("Mosh send failed", e)
            }
        }
    }

    private companion object {
        const val TAG = "LochSSH"
        val END = ByteArray(0)
        const val RECEIVE_TIMEOUT_MS = 500
        const val REOPEN_DELAY_MS = 1000L
        const val TICK_MS = 10L
        const val ACK_DELAY_MS = 20L
        const val RESEND_MS = 250L
        const val HEARTBEAT_MS = 3000L
        const val SHUTDOWN_COPIES = 3
    }
}
