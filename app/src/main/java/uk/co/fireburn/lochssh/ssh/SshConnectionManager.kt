package uk.co.fireburn.lochssh.ssh

import android.content.Context
import android.util.Log
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.android.AndroidJSch
import uk.co.fireburn.lochssh.data.db.ForwardTypes
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import kotlin.concurrent.thread

class SshConnectionManager(
    private val context: Context,
    private val config: SshConnectionConfig,
    private val onTitle: (String) -> Unit = {},
    private val onForwardError: (String) -> Unit = {},
    private val onExit: (code: Int) -> Unit
) {

    private val titleScanner = WindowTitleScanner(onTitle)
    private val connectionAttempt = ConnectionAttemptControl()
    private val connectingSessions = ConnectingSessions()
    fun interface OutputListener {
        // bytes is an internal buffer, copy it before keeping it.
        fun onOutput(bytes: ByteArray, length: Int)
    }

    @Volatile
    private var session: Session? = null
    private val jumps = mutableListOf<Session>()
    @Volatile
    private var channel: ChannelShell? = null
    private var readerThread: Thread? = null
    @Volatile
    private var remoteOut: OutputStream? = null
    // Writes go over the socket, so they must not run on the caller's thread.
    @Volatile
    private var writer: ExecutorService? = null
    private val outputListeners = CopyOnWriteArrayList<OutputListener>()
    // Retained so a listener attached after output has started (the UI polls for
    // the manager) can replay what it missed, e.g. the first shell prompt.
    private val outputLock = Any()
    private val outputHistory = ByteArrayOutputStream()
    private val dynamicForwards = mutableListOf<Socks5Forwarder>()
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
        try { connectionAttempt.run { connectChain() } }
        finally { connectingSessions.clear() }
    }

    fun cancelConnect() = connectionAttempt.cancel()
    fun closeConnectingSessions() = connectingSessions.close()

    private fun connectChain() {
        AndroidJSch.configure()
        require(config.jumpHosts.all { it.jumpHosts.isEmpty() }) { "Jump chain must be resolved before connecting" }
        var previous: Session? = null
        for (jumpConfig in config.jumpHosts) {
            connectionAttempt.checkCancelled()
            val jump = createSession(jumpConfig)
            previous?.let { jump.setProxy(JumpHostProxy(it)) }
            jumps += jump
            connectionAttempt.checkCancelled()
            jump.connect(CONNECT_TIMEOUT_MS)
            previous = jump
        }
        val s = createSession(config)
        connectionAttempt.checkCancelled()
        previous?.let { s.setProxy(JumpHostProxy(it)) }
        session = s
        s.connect(CONNECT_TIMEOUT_MS)
        connectionAttempt.checkCancelled()
        setupForwards(s)

        openShell(s)
    }

    private fun createSession(config: SshConnectionConfig): Session {
        val jsch = JSch()
        jsch.setHostKeyRepository(HostKeyTrust.repository(context))
        jsch.setInstanceLogger(object : com.jcraft.jsch.Logger {
            override fun isEnabled(level: Int) = level >= com.jcraft.jsch.Logger.INFO
            override fun log(level: Int, message: String) {
                Log.d("LochSSH-jsch", message)
            }
        })
        IdentityLoader.add(jsch, config.keyPath, config.keyMaterial, config.keyPassphrase)
        if (config.agentForwarding) {
            require(jsch.identityRepository.identities.isNotEmpty()) { "Agent forwarding needs a private key" }
            jsch.setIdentityRepository(ForwardedIdentityRepository(jsch.identityRepository))
        }

        val s = jsch.getSession(config.username, config.host, config.port)
        s.setServerAliveInterval(KeepAlive.milliseconds(config.keepAliveSeconds))
        s.setServerAliveCountMax(3)
        s.setConfig("StrictHostKeyChecking", "yes")
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
        connectingSessions.add(s)
        return s
    }

    private fun openShell(s: Session) {
        val ch = s.openChannel(CHANNEL_SHELL) as ChannelShell
        ch.setAgentForwarding(config.agentForwarding)
        ch.setPty(true)
        ch.setPtyType(PTY_TYPE, config.initialCols, config.initialRows, 0, 0)
        ch.setEnv("TERM", PTY_TYPE)
        // Both streams have to be taken before connect, or jsch wires up its
        // own and the keyboard has nothing to write to.
        val inStream = ch.inputStream
        remoteOut = ch.outputStream
        // Deliberately no timeout: jsch asks for a reply to every channel request
        // when one is set, and the window change request never gets one, so each
        // resize would block until it gave up.
        ch.connect()
        channel = ch
        writer = Executors.newSingleThreadExecutor { r -> Thread(r, "ssh-writer") }
        running = true
        readerThread = thread(name = "ssh-reader") {
            ShellOutputReader.read(
                input = inStream,
                isRunning = { running },
                onOutput = { buffer, count ->
                    titleScanner.feed(buffer, count)
                    synchronized(outputLock) {
                        appendHistory(buffer, count)
                        outputListeners.forEach { it.onOutput(buffer, count) }
                    }
                },
                onStopped = { failure ->
                    if (running) {
                        failure?.let { Log.w(TAG, "Read from ${config.host} failed", it) }
                        running = false
                        onExit(if (failure == null && ch.isClosed) ch.exitStatus else -1)
                    }
                }
            )
        }
    }

    fun write(bytes: ByteArray) {
        val out = remoteOut ?: return
        val copy = bytes.copyOf()
        try {
            writer?.execute {
                try {
                    out.write(copy)
                    out.flush()
                } catch (e: Exception) {
                    Log.w(TAG, "Write to ${config.host} failed", e)
                }
            }
        } catch (_: RejectedExecutionException) {
            // Disconnect closed the writer while this input was being queued.
        }
    }

    fun sftpBrowser(): SftpBrowser {
        val active = session?.takeIf { it.isConnected }
            ?: throw IOException("SSH session closed")
        return SftpBrowser(active)
    }

    fun discoverRemoteSessions(): RemoteSessionOptions {
        val tmuxAvailable = remoteCommand("command -v tmux >/dev/null 2>&1").exitCode == 0
        val screenAvailable = remoteCommand("command -v screen >/dev/null 2>&1").exitCode == 0
        val sessions = buildList {
            if (tmuxAvailable) {
                addAll(RemoteSessions.parseTmux(
                    remoteCommand("tmux list-sessions -F '#{session_id}|#{session_name}|#{session_attached}' 2>/dev/null").output
                ))
            }
            if (screenAvailable) {
                addAll(RemoteSessions.parseScreen(remoteCommand("screen -ls 2>/dev/null").output))
            }
        }
        return RemoteSessionOptions(tmuxAvailable, screenAvailable, sessions)
    }

    private fun remoteCommand(command: String): RemoteCommandResult {
        val active = session ?: throw IOException("SSH session closed")
        val exec = active.openChannel("exec") as ChannelExec
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        try {
            exec.setCommand(command)
            val input = exec.inputStream
            exec.connect(3_000)
            val deadline = System.nanoTime() + 3_000_000_000L
            while (System.nanoTime() < deadline) {
                val available = input.available()
                if (available > 0) {
                    val remaining = 16_384 - output.size()
                    if (remaining <= 0) throw IOException("Remote session list is too long")
                    val count = input.read(buffer, 0, minOf(available, remaining, buffer.size))
                    if (count > 0) output.write(buffer, 0, count)
                } else if (exec.isClosed) {
                    return RemoteCommandResult(exec.exitStatus, output.toString(Charsets.UTF_8.name()))
                } else {
                    Thread.sleep(20)
                }
            }
            throw IOException("Remote session check timed out")
        } finally {
            exec.disconnect()
        }
    }

    private data class RemoteCommandResult(val exitCode: Int, val output: String)

    // Sends the SSH window-change request; the remote side raises SIGWINCH.
    fun resize(cols: Int, rows: Int) {
        val ch = channel ?: return
        try {
            writer?.execute {
                try {
                    ch.setPtySize(cols, rows, 0, 0)
                } catch (e: Exception) {
                    Log.w(TAG, "Window change for ${config.host} failed", e)
                }
            }
        } catch (_: RejectedExecutionException) {
            // Disconnect closed the writer while this resize was being queued.
        }
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
        val intermediate = jumps.toList().asReversed()
        jumps.clear()
        channel = null
        session = null
        remoteOut = null
        writer?.shutdownNow()
        writer = null
        dynamicForwards.forEach { it.stop() }
        dynamicForwards.clear()
        // Closing sends packets, so it cannot run on the caller's thread either.
        thread(name = "ssh-disconnect") {
            runCatching { out?.close() }
            runCatching { ch?.disconnect() }
            runCatching { s?.disconnect() }
            intermediate.forEach { runCatching { it.disconnect() } }
        }
        synchronized(outputLock) {
            outputHistory.reset()
        }
        readerThread = null
    }

    private fun setupForwards(s: Session) {
        for (f in config.forwards) {
            try {
                when (f.type) {
                    ForwardTypes.LOCAL ->
                        Log.i(TAG, "local forward ${f.localPort} -> ${f.remoteHost}:${f.remotePort} bound to ${s.setPortForwardingL(f.localPort, f.remoteHost, f.remotePort)}")
                    ForwardTypes.REMOTE ->
                        Log.i(TAG, "remote forward ${f.remotePort} -> ${f.localPort} rc=${s.setPortForwardingR(f.remotePort, LOCAL_LOOPBACK, f.localPort)}")
                    ForwardTypes.DYNAMIC -> {
                        val forwarder = Socks5Forwarder(s, f.localPort, onForwardError)
                        forwarder.start()
                        dynamicForwards += forwarder
                        Log.i(TAG, "SOCKS5 forward listening on localhost:${f.localPort}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "forward ${f.type} ${f.localPort} failed", e)
                val port = if (f.type == ForwardTypes.REMOTE) f.remotePort else f.localPort
                val place = when (f.type) {
                    ForwardTypes.REMOTE -> "Remote"
                    ForwardTypes.DYNAMIC -> "Proxy"
                    else -> "Local"
                }
                onForwardError("$place port $port could not be opened: ${e.message ?: "connection error"}")
            }
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15000
        private const val CHANNEL_SHELL = "shell"
        private const val PTY_TYPE = "xterm-256color"
        private const val LOCAL_LOOPBACK = "127.0.0.1"
        private const val HISTORY_CAP = 1024 * 1024
        private const val TAG = "LochSSH"
    }
}
