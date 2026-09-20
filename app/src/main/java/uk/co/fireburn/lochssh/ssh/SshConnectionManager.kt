package uk.co.fireburn.lochssh.ssh

import android.content.Context
import android.util.Log
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import uk.co.fireburn.lochssh.data.db.ForwardTypes
import java.io.File
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

class SshConnectionManager(
    private val context: Context,
    private val config: SshConnectionConfig,
    private val onExit: (code: Int) -> Unit
) {
    fun interface OutputListener {
        // bytes is an internal buffer, copy it before keeping it.
        fun onOutput(bytes: ByteArray, length: Int)
    }

    private var session: Session? = null
    private var channel: ChannelShell? = null
    private var readerThread: Thread? = null
    private val pipedOut = PipedOutputStream()
    private val pipedIn = PipedInputStream(pipedOut, 64 * 1024)
    private val outputListeners = CopyOnWriteArrayList<OutputListener>()
    private var tempKeyFile: File? = null
    @Volatile
    private var running = false

    val isConnected: Boolean
        get() = session?.isConnected == true

    fun addOutputListener(listener: OutputListener) {
        outputListeners.add(listener)
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
        ch.setInputStream(pipedIn)
        ch.connect(CHANNEL_TIMEOUT_MS)
        channel = ch
        running = true

        readerThread = thread(name = "ssh-reader") {
            val inStream = ch.getInputStream()
            val buffer = ByteArray(READ_BUFFER_SIZE)
            while (running) {
                val read = inStream.read(buffer)
                if (read == -1) break
                if (read > 0) outputListeners.forEach { it.onOutput(buffer, read) }
            }
            if (running) {
                running = false
                onExit(if (ch.isClosed) ch.exitStatus else -1)
            }
        }
    }

    fun write(bytes: ByteArray) {
        try {
            pipedOut.write(bytes)
            pipedOut.flush()
        } catch (_: Exception) {
        }
    }

    // Sends the SSH window-change request; the remote side raises SIGWINCH.
    fun resize(cols: Int, rows: Int) {
        channel?.setPtySize(cols, rows, 0, 0)
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

    @Synchronized
    fun disconnect() {
        running = false
        try {
            channel?.disconnect()
        } catch (_: Exception) {
        }
        try {
            session?.disconnect()
        } catch (_: Exception) {
        }
        try {
            pipedOut.close()
        } catch (_: Exception) {
        }
        readerThread = null
        deleteTempKey()
    }

    private fun setupForwards(s: Session) {
        for (f in config.forwards) {
            when (f.type) {
                ForwardTypes.LOCAL -> s.setPortForwardingL(f.localPort, f.remoteHost, f.remotePort)
                ForwardTypes.REMOTE -> s.setPortForwardingR(f.remotePort, LOCAL_LOOPBACK, f.localPort)
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
    }
}
