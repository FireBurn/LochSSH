package uk.co.fireburn.lochssh.ssh

import android.content.Context
import android.util.Log
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import uk.co.fireburn.lochssh.data.db.ForwardTypes
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.concurrent.thread

class SshConnectionManager(
    private val context: Context,
    private val config: SshConnectionConfig,
    private val onTitle: (String) -> Unit = {},
    private val onExit: (code: Int) -> Unit
) {

    private val titleScanner = WindowTitleScanner(onTitle)
    fun interface OutputListener {
        // bytes is an internal buffer, copy it before keeping it.
        fun onOutput(bytes: ByteArray, length: Int)
    }

    private var session: Session? = null
    private var channel: ChannelShell? = null
    private var readerThread: Thread? = null
    private var remoteOut: OutputStream? = null
    // Writes go over the socket, so they must not run on the caller's thread.
    private var writer: ExecutorService? = null
    private val outputListeners = CopyOnWriteArrayList<OutputListener>()
    // Retained so a listener attached after output has started (the UI polls for
    // the manager) can replay what it missed, e.g. the first shell prompt.
    private val outputLock = Any()
    private val outputHistory = ByteArrayOutputStream()
    private var tempKeyFile: File? = null
    @Volatile
    private var running = false

    val isConnected: Boolean
        get() = session?.isConnected == true

    fun addOutputListener(listener: OutputListener) {
        synchronized(outputLock) {
            outputListeners.add(listener)
            val replay = outputHistory.toByteArray()
            if (replay.isNotEmpty()) listener.onOutput(replay, replay.size)
        }
    }

    fun removeOutputListener(listener: OutputListener) {
        outputListeners.remove(listener)
    }

    @Synchronized
    fun connect() {
        val jsch = JSch()
        jsch.setInstanceLogger(object : com.jcraft.jsch.Logger {
            override fun isEnabled(level: Int) = level >= com.jcraft.jsch.Logger.INFO
            override fun log(level: Int, message: String) {
                Log.d("LochSSH-jsch", message)
            }
        })
        try {
            loadIdentity(jsch)
        } catch (e: Exception) {
            deleteTempKey()
            throw e
        }

        val s = jsch.getSession(config.username, config.host, config.port)
        s.setServerAliveInterval(config.keepAliveSeconds.coerceAtLeast(5) * 1000)
        s.setServerAliveCountMax(3)
        s.setConfig("StrictHostKeyChecking", "accept-new")
        s.setConfig(
            "server_host_key",
            "ssh-ed25519,ecdsa-sha2-nistp256,rsa-sha2-512,rsa-sha2-256,ssh-rsa"
        )
        s.setConfig(
            "kex",
            "curve25519-sha256,curve25519-sha256@libssh.org,ecdh-sha2-nistp256," +
                "diffie-hellman-group-exchange-sha256,diffie-hellman-group16-sha512," +
                "diffie-hellman-group18-sha512,diffie-hellman-group14-sha256"
        )
        s.setConfig(
            "cipher",
            "chacha20-poly1305@openssh.com,aes256-gcm@openssh.com,aes128-gcm@openssh.com," +
                "aes256-ctr,aes192-ctr,aes128-ctr"
        )
        config.password?.let { s.setPassword(it) }
        s.connect(CONNECT_TIMEOUT_MS)
        session = s
        setupForwards(s)

        val ch = s.openChannel(CHANNEL_SHELL) as ChannelShell
        ch.setPty(true)
        ch.setPtyType(PTY_TYPE, config.initialCols, config.initialRows, 0, 0)
        ch.setEnv("TERM", PTY_TYPE)
        // Both streams have to be taken before connect, or jsch wires up its
        // own and the keyboard has nothing to write to.
        val inStream = ch.inputStream
        remoteOut = ch.outputStream
        ch.connect(CHANNEL_TIMEOUT_MS)
        channel = ch
        writer = Executors.newSingleThreadExecutor { r -> Thread(r, "ssh-writer") }
        running = true

        readerThread = thread(name = "ssh-reader") {
            val buffer = ByteArray(READ_BUFFER_SIZE)
            while (running) {
                val read = inStream.read(buffer)
                if (read == -1) break
                if (read > 0) {
                    titleScanner.feed(buffer, read)
                    synchronized(outputLock) {
                        appendHistory(buffer, read)
                        outputListeners.forEach { it.onOutput(buffer, read) }
                    }
                }
            }
            if (running) {
                running = false
                onExit(if (ch.isClosed) ch.exitStatus else -1)
            }
        }
    }

    fun write(bytes: ByteArray) {
        val out = remoteOut ?: return
        val copy = bytes.copyOf()
        writer?.execute {
            try {
                out.write(copy)
                out.flush()
            } catch (e: Exception) {
                Log.w(TAG, "Write to ${config.host} failed", e)
            }
        }
    }

    // Sends the SSH window-change request; the remote side raises SIGWINCH.
    fun resize(cols: Int, rows: Int) {
        val ch = channel ?: return
        writer?.execute {
            try {
                ch.setPtySize(cols, rows, 0, 0)
            } catch (e: Exception) {
                Log.w(TAG, "Window change for ${config.host} failed", e)
            }
        }
    }

    // Pasted or generated keys are written to app-private storage so the
    // classic addIdentity(path) API can load them.
    private fun loadIdentity(jsch: JSch) {
        val keyFile = config.keyPath ?: config.keyMaterial?.let { writeTempKey(it) }
        if (keyFile != null) {
            val passphrase = config.keyPassphrase
            if (passphrase.isNullOrBlank()) jsch.addIdentity(keyFile) else jsch.addIdentity(keyFile, passphrase)
        }
    }

    private fun writeTempKey(material: String): String {
        val f = File(context.filesDir, "lochssh_key_${System.nanoTime()}")
        f.writeText(material)
        try {
            f.setReadable(false, true)
            f.setReadable(true, false)
            f.setWritable(false, true)
            f.setWritable(true, false)
        } catch (_: Exception) {
        }
        tempKeyFile = f
        return f.absolutePath
    }

    private fun deleteTempKey() {
        tempKeyFile?.let { runCatching { it.delete() } }
        tempKeyFile = null
    }

    private fun appendHistory(bytes: ByteArray, length: Int) {
        outputHistory.write(bytes, 0, length)
        if (outputHistory.size() > HISTORY_CAP) {
            val all = outputHistory.toByteArray()
            outputHistory.reset()
            outputHistory.write(all, all.size - HISTORY_CAP, HISTORY_CAP)
        }
    }

    @Synchronized
    fun disconnect() {
        running = false
        val ch = channel
        val s = session
        val out = remoteOut
        channel = null
        session = null
        remoteOut = null
        writer?.shutdownNow()
        writer = null
        // Closing sends packets, so it cannot run on the caller's thread either.
        thread(name = "ssh-disconnect") {
            runCatching { out?.close() }
            runCatching { ch?.disconnect() }
            runCatching { s?.disconnect() }
        }
        synchronized(outputLock) {
            outputHistory.reset()
        }
        readerThread = null
        deleteTempKey()
    }

    private fun setupForwards(s: Session) {
        for (f in config.forwards) {
            try {
                when (f.type) {
                    ForwardTypes.LOCAL ->
                        Log.i(TAG, "local forward ${f.localPort} -> ${f.remoteHost}:${f.remotePort} bound to ${s.setPortForwardingL(f.localPort, f.remoteHost, f.remotePort)}")
                    ForwardTypes.REMOTE ->
                        Log.i(TAG, "remote forward ${f.remotePort} -> ${f.localPort} rc=${s.setPortForwardingR(f.remotePort, LOCAL_LOOPBACK, f.localPort)}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "forward ${f.type} ${f.localPort} failed", e)
            }
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15000
        private const val CHANNEL_TIMEOUT_MS = 10000
        private const val READ_BUFFER_SIZE = 8192
        private const val CHANNEL_SHELL = "shell"
        private const val PTY_TYPE = "xterm-256color"
        private const val LOCAL_LOOPBACK = "127.0.0.1"
        private const val HISTORY_CAP = 1024 * 1024
        private const val TAG = "LochSSH"
    }
}
