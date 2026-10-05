package uk.co.fireburn.lochssh.ssh

import android.content.Context
import android.util.Log
import com.jcraft.jsch.Channel
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.android.AndroidJSch
import uk.co.fireburn.lochssh.data.db.ForwardTypes
import uk.co.fireburn.lochssh.data.db.RemoteSessionModes
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import uk.co.fireburn.lochssh.ui.terminal.TerminalSession
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking

class SshConnectionManager(
    private val context: Context,
    private val config: SshConnectionConfig,
    private val sharedConnection: SshConnectionManager? = null,
    private val startupCommand: String? = null,
    private val onTitle: (String) -> Unit = {},
    private val onForwardError: (String) -> Unit = {},
    private val onExit: (code: Int) -> Unit
) {

    private val titleScanner = WindowTitleScanner(onTitle)
    private val connectionAttempt = ConnectionAttemptControl()
    private val connectingSessions = ConnectingSessions()
    private val connectingChannels = ConnectingChannels()
    private val sessionChannels = SessionChannels()
    private val windowSize = AtomicReference(config.initialCols to config.initialRows)
    internal val recording = RecordingCapture()
    val terminal = TerminalSession(config.initialCols, config.initialRows) {
        writeBytes(it.toByteArray(Charsets.US_ASCII))
    }

    @Volatile
    private var session: Session? = null
    private val jumps = mutableListOf<Session>()
    @Volatile
    private var channel: Channel? = null
    private var readerThread: Thread? = null
    @Volatile
    private var remoteOut: OutputStream? = null
    // Writes go over the socket, so they must not run on the caller's thread.
    @Volatile
    private var writer: ExecutorService? = null
    private val dynamicForwards = mutableListOf<Socks5Forwarder>()
    @Volatile
    private var transport: SharedSshConnection? = null
    private var warningSubscription: AutoCloseable? = null
    @Volatile
    private var running = false

    var startupOptions: RemoteSessionOptions? = null
        private set

    @Volatile private var hasInput = false
    val isUnusedShell: Boolean get() = ShellCleanup.canClose(config, startupCommand, hasInput, terminal.buffer)

    val isConnected: Boolean
        get() = running && channel?.isConnected == true && session?.isConnected == true

    @Synchronized
    fun connect() {
        try { connectionAttempt.run { connectChain() } }
        finally {
            connectingChannels.clear()
            connectingSessions.clear()
        }
    }

    fun cancelConnect() = connectionAttempt.cancel()
    fun closeConnectingSessions() {
        connectingChannels.close()
        connectingSessions.close()
    }

    fun canShare(other: SshConnectionConfig): Boolean =
        config.sameTransport(other) && transport?.isConnected == true

    private fun acquireTransport(): SharedSshConnection? = transport?.takeIf { it.acquire() }

    private fun connectChain() {
        SshProxyRoute.validate(config)
        AndroidJSch.configure()
        val reused = sharedConnection?.acquireTransport()
        if (reused != null) {
            transport = reused
            session = reused.session
            warningSubscription = reused.warnings.listen(onForwardError)
            connectionAttempt.checkCancelled()
            openShell(reused.session)
            return
        }
        require(config.jumpHosts.all { it.jumpHosts.isEmpty() }) { "Jump chain must be resolved before connecting" }
        require(config.jumpHosts.isEmpty() || config.knockSequence.isBlank()) {
            "Port knocking is only available for directly reachable hosts"
        }
        var previous: Session? = null
        for (jumpConfig in config.jumpHosts) {
            connectionAttempt.checkCancelled()
            require(previous == null || jumpConfig.knockSequence.isBlank()) {
                "Port knocking is only available on the first jump host"
            }
            if (previous == null) knock(jumpConfig)
            val jump = createSession(jumpConfig)
            previous?.let { jump.setProxy(JumpHostProxy(it)) }
            jumps += jump
            connectionAttempt.checkCancelled()
            jump.connect(CONNECT_TIMEOUT_MS)
            previous = jump
        }
        val s = createSession(config)
        connectionAttempt.checkCancelled()
        if (previous == null) knock(config)
        previous?.let { s.setProxy(JumpHostProxy(it)) }
        session = s
        s.connect(CONNECT_TIMEOUT_MS)
        connectionAttempt.checkCancelled()
        val warnings = ForwardWarnings()
        warningSubscription = warnings.listen(onForwardError)
        setupForwards(s, warnings::report)

        transport = SharedSshConnection(s, jumps.toList(), dynamicForwards.toList(), warnings)

        openShell(s)
    }

    private fun knock(target: SshConnectionConfig) {
        connectionAttempt.checkCancelled()
        runBlocking { PortKnocking.send(target.host, KnockSequence.parse(target.knockSequence), target.knockPause) }
        connectionAttempt.checkCancelled()
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
        val proxy = config.proxy?.create()
        proxy?.let(s::setProxy)
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
        connectingSessions.add(s, proxy)
        return s
    }

    private fun openShell(s: Session) {
        val initialSize = windowSize.get()
        val command = startupCommand ?: automaticCommand()
        val ch = TerminalChannel.open(s, command, initialSize.first, initialSize.second, config.agentForwarding)
        channel = ch
        connectingChannels.add(ch)
        // Both streams have to be taken before connect, or jsch wires up its
        // own and the keyboard has nothing to write to.
        val inStream = ch.inputStream
        remoteOut = ch.outputStream
        // Deliberately no timeout: jsch asks for a reply to every channel request
        // when one is set, and the window change request never gets one, so each
        // resize would block until it gave up.
        connectionAttempt.checkCancelled()
        ch.connect()
        connectionAttempt.checkCancelled()
        writer = Executors.newSingleThreadExecutor { r -> Thread(r, "ssh-writer") }
        queueResize(ch)
        running = true
        readerThread = thread(name = "ssh-reader") {
            ShellOutputReader.read(
                input = inStream,
                isRunning = { running },
                onOutput = { buffer, count ->
                    recording.append(buffer, count)
                    titleScanner.feed(buffer, count)
                    terminal.feed(buffer, count)
                },
                onStopped = { failure ->
                    recording.close()
                    if (running) {
                        failure?.let { Log.w(TAG, "Read from ${config.host} failed", it) }
                        running = false
                        onExit(if (failure == null && ch.isClosed) ch.exitStatus else -1)
                    }
                }
            )
        }
    }

    private fun automaticCommand(): String? {
        val mode = config.remoteSessionMode
        if (mode != RemoteSessionModes.TMUX && mode != RemoteSessionModes.SCREEN) return null
        val options = RemoteSessions.startupOptions(mode, ::remoteSessionAvailable, ::discoverRemoteSessions)
        startupOptions = options
        return RemoteSessions.startupCommand(mode, options)
    }

    fun write(bytes: ByteArray) {
        if (bytes.isNotEmpty()) hasInput = true
        writeBytes(bytes)
    }

    private fun writeBytes(bytes: ByteArray) {
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
        val tmuxAvailable = remoteSessionAvailable(RemoteSessionModes.TMUX)
        val screenAvailable = remoteSessionAvailable(RemoteSessionModes.SCREEN)
        val sessions = buildList {
            if (tmuxAvailable) {
                val panes = remoteCommand("tmux list-sessions -F '#{session_id}|#{session_name}\t#{pane_current_command}\t#{window_name}\t#{pane_pid}|#{session_attached}' 2>/dev/null").output
                val processes = try { remoteCommand(RemoteTasks.command, 262_144).output }
                    catch (_: Exception) { "" }
                addAll(RemoteSessions.parseTmux(
                    RemoteTasks.enrich(panes, processes)
                ))
            }
            if (screenAvailable) {
                addAll(RemoteSessions.parseScreen(remoteCommand("screen -ls 2>/dev/null").output))
            }
        }
        return RemoteSessionOptions(tmuxAvailable, screenAvailable, sessions)
    }

    fun remoteSessionAvailable(tool: String): Boolean =
        remoteCommand(RemoteSessions.availabilityCommand(tool)).exitCode == 0

    fun readPerformance(): PerformanceSnapshot {
        val result = remoteCommand(PerformanceCommand.command, PerformanceParser.MAX_OUTPUT)
        if (result.exitCode != 0) throw IOException("Linux performance readings are not available on this server")
        return PerformanceParser.parse(result.output)
    }

    private fun remoteCommand(command: String, maxBytes: Int = 16_384): RemoteCommandResult {
        val active = session ?: throw IOException("SSH session closed")
        val exec = active.openChannel("exec") as ChannelExec
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        try {
            sessionChannels.add(exec)
            exec.setCommand(command)
            val input = exec.inputStream
            exec.connect(3_000)
            val deadline = System.nanoTime() + 3_000_000_000L
            while (System.nanoTime() < deadline) {
                if (session !== active) throw IOException("Terminal closed")
                val available = input.available()
                if (available > 0) {
                    val remaining = maxBytes - output.size()
                    if (remaining <= 0) throw IOException("Server response is too long")
                    val count = input.read(buffer, 0, minOf(available, remaining, buffer.size))
                    if (count > 0) output.write(buffer, 0, count)
                } else if (exec.isClosed) {
                    return RemoteCommandResult(exec.exitStatus, output.toString(Charsets.UTF_8.name()))
                } else {
                    Thread.sleep(20)
                }
            }
            throw IOException("Server command timed out")
        } finally {
            sessionChannels.remove(exec)
            exec.disconnect()
        }
    }

    private data class RemoteCommandResult(val exitCode: Int, val output: String)

    // Sends the SSH window-change request; the remote side raises SIGWINCH.
    fun resize(cols: Int, rows: Int) {
        require(cols > 0 && rows > 0)
        windowSize.set(cols to rows)
        val ch = channel ?: return
        queueResize(ch)
    }

    private fun queueResize(ch: Channel) {
        try {
            writer?.execute {
                try {
                    val size = windowSize.get()
                    TerminalChannel.resize(ch, size.first, size.second)
                } catch (e: Exception) {
                    Log.w(TAG, "Window change for ${config.host} failed", e)
                }
            }
        } catch (_: RejectedExecutionException) {
            // Disconnect closed the writer while this resize was being queued.
        }
    }

    @Synchronized
    fun disconnect() {
        running = false
        recording.close()
        warningSubscription?.close()
        warningSubscription = null
        val ch = channel
        val s = session
        val out = remoteOut
        val shared = transport
        transport = null
        val intermediate = if (shared == null) jumps.toList().asReversed() else emptyList()
        jumps.clear()
        channel = null
        session = null
        remoteOut = null
        writer?.shutdownNow()
        writer = null
        if (shared == null) dynamicForwards.forEach { it.stop() }
        dynamicForwards.clear()
        // Closing sends packets, so it cannot run on the caller's thread either.
        thread(name = "ssh-disconnect") {
            sessionChannels.close()
            runCatching { out?.close() }
            runCatching { ch?.disconnect() }
            if (shared != null) shared.release()
            else {
                runCatching { s?.disconnect() }
                intermediate.forEach { runCatching { it.disconnect() } }
            }
        }
        readerThread = null
    }

    private fun setupForwards(s: Session, report: (String) -> Unit) {
        for (f in config.forwards) {
            try {
                when (f.type) {
                    ForwardTypes.LOCAL ->
                        Log.i(TAG, "local forward ${f.localPort} -> ${f.remoteHost}:${f.remotePort} bound to ${s.setPortForwardingL(f.localPort, f.remoteHost, f.remotePort)}")
                    ForwardTypes.REMOTE ->
                        Log.i(TAG, "remote forward ${f.remotePort} -> ${f.localPort} rc=${s.setPortForwardingR(f.remotePort, LOCAL_LOOPBACK, f.localPort)}")
                    ForwardTypes.DYNAMIC -> {
                        val forwarder = Socks5Forwarder(s, f.localPort, report)
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
                report("$place port $port could not be opened: ${e.message ?: "connection error"}")
            }
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15000
        private const val CHANNEL_SHELL = "shell"
        private const val PTY_TYPE = "xterm-256color"
        private const val LOCAL_LOOPBACK = "127.0.0.1"
        private const val TAG = "LochSSH"
    }
}
