package uk.co.fireburn.lochssh.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import uk.co.fireburn.lochssh.MainActivity
import uk.co.fireburn.lochssh.R
import uk.co.fireburn.lochssh.data.EncryptedStorageManager
import uk.co.fireburn.lochssh.data.ConnectionHistoryRecorder
import uk.co.fireburn.lochssh.data.db.AuthTypes
import uk.co.fireburn.lochssh.data.db.ConnectionStates
import uk.co.fireburn.lochssh.data.db.IdentityDao
import uk.co.fireburn.lochssh.data.db.PortForwardDao
import uk.co.fireburn.lochssh.data.db.RemoteSessionModes
import uk.co.fireburn.lochssh.data.db.SshHostDao
import uk.co.fireburn.lochssh.data.db.SshHostEntity
import uk.co.fireburn.lochssh.ssh.JumpHostChain
import uk.co.fireburn.lochssh.ssh.HostKeyReviewRequired
import uk.co.fireburn.lochssh.ssh.SavedHostKey
import uk.co.fireburn.lochssh.ssh.PortForwardSpec
import uk.co.fireburn.lochssh.ssh.RemoteSessionOptions
import uk.co.fireburn.lochssh.ssh.RemoteSessions
import uk.co.fireburn.lochssh.ssh.SessionRegistry
import uk.co.fireburn.lochssh.ssh.SshConnectionConfig
import uk.co.fireburn.lochssh.ssh.SshConnectionManager
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import javax.inject.Inject
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@AndroidEntryPoint
class SshForegroundService : Service() {

    private class ConnectionAttempt(
        val sessionId: Long, val hostId: Long, val token: String, val remoteCommand: String?
    ) {
        @Volatile
        var cancelled = false
        @Volatile
        var manager: SshConnectionManager? = null
    }

    private data class ConnectedSession(
        val manager: SshConnectionManager,
        val config: SshConnectionConfig
    )

    @Inject
    lateinit var hostDao: SshHostDao
    @Inject
    lateinit var identityDao: IdentityDao
    @Inject
    lateinit var portForwardDao: PortForwardDao
    @Inject
    lateinit var secrets: EncryptedStorageManager
    @Inject
    lateinit var registry: SessionRegistry
    @Inject
    lateinit var history: ConnectionHistoryRecorder

    private val managers = ConcurrentHashMap<Long, SshConnectionManager>()
    private val connectedTokens = ConcurrentHashMap<Long, String>()
    private val attempts = ConcurrentHashMap<Long, ConnectionAttempt>()
    private val hostLocks = ConcurrentHashMap<Long, Mutex>()
    private var wakeLock: PowerManager.WakeLock? = null
    private var stopReason: String? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        history.start()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android gives us five seconds to go foreground, which is less than a
        // connection can take, so the summary goes up before anything else.
        startForegroundSummary()

        if (intent?.action == ACTION_DISCONNECT) {
            endSession(intent.getLongExtra(EXTRA_SESSION_ID, -1L))
            return Service.START_NOT_STICKY
        }

        val hostId = intent?.getLongExtra(EXTRA_HOST_ID, -1L) ?: -1L
        val sessionId = intent?.getLongExtra(EXTRA_SESSION_ID, -1L) ?: -1L
        if (hostId == -1L || sessionId == -1L) {
            stopIfIdle()
            return Service.START_NOT_STICKY
        }
        val attempt = ConnectionAttempt(
            sessionId, hostId, UUID.randomUUID().toString(),
            intent?.getStringExtra(EXTRA_REMOTE_COMMAND)?.takeIf { it.length <= 1024 && '\n' !in it && '\r' !in it }
        )
        attempts.put(sessionId, attempt)?.let(::cancelAttempt)
        history.started(attempt.token, hostId)
        registry.opening(sessionId, hostId, "Host")
        scope.launch { connect(attempt) }
        return Service.START_NOT_STICKY
    }

    private suspend fun connect(attempt: ConnectionAttempt) {
        hostLocks.computeIfAbsent(attempt.hostId) { Mutex() }.withLock {
            if (attempts[attempt.sessionId] === attempt && !attempt.cancelled) {
                connectLocked(attempt)
            }
        }
    }

    private suspend fun connectLocked(attempt: ConnectionAttempt) {
        val sessionId = attempt.sessionId
        val hostId = attempt.hostId
        try {
            val host = hostDao.getById(hostId)
            if (host == null) throw IllegalStateException("Host not found")
            if (attempts[sessionId] !== attempt) return
            history.host(attempt.token, host.name, host.host, host.port)
            registry.opening(sessionId, hostId, host.name)
            // Key loading and the SSH handshake block, so keep them off the main thread.
            val connected = withContext(Dispatchers.IO) {
                val jumpHosts = JumpHostChain.resolve(host, hostDao::getById)
                suspend fun configFor(host: SshHostEntity): SshConnectionConfig {
                    val identity = host.identityId?.let { identityDao.getById(it) }
                    val secret = identity?.secretRef?.let { secrets.getSecret(it) }
                    val keyMaterial = identity?.keyMaterialRef?.let { secrets.getSecret(it) }

                    return SshConnectionConfig(
                        host = host.host,
                        port = host.port,
                        username = identity?.username ?: "",
                        authType = identity?.authType ?: AuthTypes.NONE,
                        password = if (identity?.authType == AuthTypes.PASSWORD) secret else null,
                        keyPath = identity?.keyPath,
                        keyMaterial = keyMaterial,
                        keyPassphrase = if (identity?.authType == AuthTypes.PUBLIC_KEY) secret else null,
                        keepAliveSeconds = host.keepAliveSeconds,
                        autoCommand = host.autoCommand,
                        remoteSessionMode = host.remoteSessionMode,
                        agentForwarding = host.id == hostId && host.agentForwarding,
                        forwards = if (host.id == hostId) portForwardDao.getByHost(hostId).map {
                            PortForwardSpec(it.type, it.localPort, it.remoteHost, it.remotePort)
                        } else emptyList()
                    )
                }
                val config = configFor(host).copy(jumpHosts = jumpHosts.map { configFor(it) })
                val shared = managers.entries.firstOrNull { (id, candidate) ->
                    registry.find(id)?.hostId == hostId && candidate.canShare(config)
                }?.value

                lateinit var manager: SshConnectionManager
                manager = SshConnectionManager(
                    context = this@SshForegroundService,
                    config = config,
                    sharedConnection = shared,
                    onTitle = { title -> scope.launch { onSessionTitle(sessionId, manager, title) } },
                    onForwardError = { message ->
                        scope.launch { onForwardError(sessionId, manager, message) }
                    }
                ) { code ->
                    scope.launch { onSessionExit(sessionId, manager, code) }
                }
                synchronized(attempt) {
                    if (attempt.cancelled) return@withContext null
                    attempt.manager = manager
                }
                manager.connect()
                if (attempt.cancelled) {
                    manager.disconnect()
                    null
                } else {
                    ConnectedSession(manager, config)
                }
            }
            if (connected == null || attempts[sessionId] !== attempt || attempt.cancelled) {
                connected?.manager?.disconnect()
                return
            }
            attempts.remove(sessionId, attempt)
            managers[sessionId] = connected.manager
            connectedTokens[sessionId] = attempt.token
            history.connected(attempt.token)
            registry.connected(sessionId, connected.manager)
            showSessionNotification(sessionId, host.name, "Connected to ${host.host}")
            updateSummary()
            scope.launch(Dispatchers.IO) {
                startRemoteSession(sessionId, connected.config, connected.manager, attempt.remoteCommand)
            }
        } catch (e: Exception) {
            attempt.manager?.disconnect()
            if (attempts.remove(sessionId, attempt)) {
                history.finished(attempt.token, ConnectionStates.FAILED)
                Log.e(TAG, "Connect to host $hostId failed", e)
                fail(sessionId, e.message ?: e.javaClass.simpleName, (e as? HostKeyReviewRequired)?.key)
            }
        }
    }

    private fun startRemoteSession(
        sessionId: Long,
        config: SshConnectionConfig,
        manager: SshConnectionManager,
        remoteCommand: String?
    ) {
        if (managers[sessionId] !== manager) return
        if (remoteCommand != null) {
            manager.write((remoteCommand + "\n").toByteArray(Charsets.UTF_8))
            return
        }
        if (config.remoteSessionMode == RemoteSessionModes.SHELL) {
            if (config.autoCommand.isNotBlank()) {
                manager.write((config.autoCommand + "\n").toByteArray(Charsets.UTF_8))
            }
            return
        }
        val options = try {
            manager.discoverRemoteSessions()
        } catch (e: Exception) {
            Log.w(TAG, "Could not check remote sessions", e)
            RemoteSessionOptions(error = "Could not check remote sessions")
        }
        if (managers[sessionId] !== manager) return
        val command = when (config.remoteSessionMode) {
            RemoteSessionModes.TMUX -> if (options.tmuxAvailable) RemoteSessions.automatic(RemoteSessionModes.TMUX) else null
            RemoteSessionModes.SCREEN -> if (options.screenAvailable) RemoteSessions.automatic(RemoteSessionModes.SCREEN) else null
            else -> null
        }
        if (command != null) {
            manager.write((command + "\n").toByteArray(Charsets.UTF_8))
        } else {
            val missing = when (config.remoteSessionMode) {
                RemoteSessionModes.TMUX -> "tmux is not installed"
                RemoteSessionModes.SCREEN -> "Screen is not installed"
                else -> null
            }
            scope.launch {
                if (managers[sessionId] === manager) {
                    registry.offerRemoteSessions(sessionId, options.copy(error = options.error ?: missing))
                }
            }
        }
    }

    private fun onSessionTitle(sessionId: Long, manager: SshConnectionManager, title: String) {
        if (managers[sessionId] !== manager && attempts[sessionId]?.manager !== manager) return
        registry.titled(sessionId, title)
        val session = registry.find(sessionId) ?: return
        if (managers[sessionId] === manager) {
            showSessionNotification(sessionId, session.label, "Connected to ${session.hostName}")
        }
    }

    private fun onForwardError(sessionId: Long, manager: SshConnectionManager, message: String) {
        if (managers[sessionId] !== manager && attempts[sessionId]?.manager !== manager) return
        registry.addForwardWarning(sessionId, message)
    }

    private fun onSessionExit(sessionId: Long, manager: SshConnectionManager, code: Int) {
        if (managers[sessionId] !== manager && attempts[sessionId]?.manager !== manager) return
        Log.i(TAG, "Session $sessionId exited with code $code")
        endSession(sessionId)
    }

    private fun endSession(sessionId: Long) {
        attempts.remove(sessionId)?.let(::cancelAttempt)
        managers.remove(sessionId)?.disconnect()
        connectedTokens.remove(sessionId)?.let { history.finished(it, ConnectionStates.ENDED) }
        registry.remove(sessionId)
        NotificationManagerCompat.from(this).cancel(sessionNotificationId(sessionId))
        stopIfIdle()
    }

    private fun fail(sessionId: Long, message: String, review: SavedHostKey? = null) {
        Log.e(TAG, "Session $sessionId failed: $message")
        registry.failed(sessionId, "Connection failed: $message", review)
        NotificationManagerCompat.from(this).cancel(sessionNotificationId(sessionId))
        stopIfIdle()
    }

    private fun stopIfIdle() {
        if (managers.isEmpty() && attempts.isEmpty()) {
            stopSelf()
        } else {
            updateSummary()
        }
    }

    private fun cancelAttempt(attempt: ConnectionAttempt) {
        val manager = synchronized(attempt) {
            attempt.cancelled = true
            attempt.manager
        }
        history.finished(attempt.token, ConnectionStates.CANCELLED)
        if (manager != null) {
            manager.cancelConnect()
            thread(name = "ssh-cancel") {
                manager.closeConnectingSessions()
                manager.disconnect()
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SSH connections",
            NotificationManager.IMPORTANCE_LOW
        )
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun resumeIntent(sessionId: Long): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_SESSION_ID, sessionId)
        return PendingIntent.getActivity(
            this,
            sessionId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun disconnectIntent(sessionId: Long): PendingIntent {
        val intent = Intent(this, SshForegroundService::class.java)
            .setAction(ACTION_DISCONNECT)
            .putExtra(EXTRA_SESSION_ID, sessionId)
        return PendingIntent.getService(
            this,
            DISCONNECT_REQUEST_BASE + sessionId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun showSessionNotification(sessionId: Long, title: String, text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setGroup(GROUP_KEY)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(resumeIntent(sessionId))
            .addAction(0, "Disconnect", disconnectIntent(sessionId))
            .build()
        notifyIfAllowed(sessionNotificationId(sessionId), notification)
    }

    private fun summaryNotification(): android.app.Notification {
        val count = managers.size
        val text = when (count) {
            0 -> "Connecting"
            1 -> "1 session"
            else -> "$count sessions"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("LochSSH")
            .setContentText(text)
            .setOngoing(true)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    // The foreground notification goes up regardless, but the per session ones
    // need a permission the user can refuse.
    private fun notifyIfAllowed(id: Int, notification: android.app.Notification) {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            NotificationManagerCompat.from(this).notify(id, notification)
        }
    }

    private fun startForegroundSummary() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                SUMMARY_NOTIFICATION_ID,
                summaryNotification(),
                SshServiceType.forAndroid(Build.VERSION.SDK_INT)
            )
        } else {
            startForeground(SUMMARY_NOTIFICATION_ID, summaryNotification())
        }
    }

    private fun updateSummary() {
        notifyIfAllowed(SUMMARY_NOTIFICATION_ID, summaryNotification())
    }

    // Doze still requires the user to allow a battery exemption.
    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .apply { acquire(WAKE_LOCK_TIMEOUT_MS) }
    }

    override fun onTimeout(startId: Int) = stopAfterTimeout()

    override fun onTimeout(startId: Int, fgsType: Int) = stopAfterTimeout()

    private fun stopAfterTimeout() {
        Log.w(TAG, "Android timed out the SSH service")
        stopReason = "Android stopped background connections. Retry to connect again."
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        val ids = (attempts.keys + managers.keys).toSet()
        val attemptTokens = attempts.values.map { it.token }
        attempts.values.forEach(::cancelAttempt)
        attempts.clear()
        managers.values.forEach { it.disconnect() }
        registry.closeSessions(ids, stopReason)
        ids.forEach { NotificationManagerCompat.from(this).cancel(sessionNotificationId(it)) }
        managers.clear()
        val status = if (stopReason == null) ConnectionStates.ENDED else ConnectionStates.INTERRUPTED
        connectedTokens.values.forEach { history.finished(it, status) }
        if (stopReason != null) attemptTokens.forEach { history.finished(it, ConnectionStates.INTERRUPTED) }
        connectedTokens.clear()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_SESSION_ID = "session_id"

        private const val EXTRA_HOST_ID = "host_id"
        private const val EXTRA_REMOTE_COMMAND = "remote_command"
        private const val ACTION_DISCONNECT = "uk.co.fireburn.lochssh.DISCONNECT"
        private const val CHANNEL_ID = "ssh_connections"
        private const val GROUP_KEY = "uk.co.fireburn.lochssh.sessions"
        private const val SUMMARY_NOTIFICATION_ID = 1
        private const val SESSION_NOTIFICATION_BASE = 1000
        private const val DISCONNECT_REQUEST_BASE = 5000
        private const val WAKE_LOCK_TAG = "lochssh:ssh-service"
        private const val WAKE_LOCK_TIMEOUT_MS = 12L * 60 * 60 * 1000
        private const val TAG = "LochSSH"

        private fun sessionNotificationId(sessionId: Long) =
            SESSION_NOTIFICATION_BASE + sessionId.toInt()

        fun start(context: Context, hostId: Long, sessionId: Long, remoteCommand: String? = null) {
            val intent = Intent(context, SshForegroundService::class.java)
                .putExtra(EXTRA_HOST_ID, hostId)
                .putExtra(EXTRA_SESSION_ID, sessionId)
                .putExtra(EXTRA_REMOTE_COMMAND, remoteCommand)
            context.startForegroundService(intent)
        }

        fun disconnect(context: Context, sessionId: Long) {
            val intent = Intent(context, SshForegroundService::class.java)
                .setAction(ACTION_DISCONNECT)
                .putExtra(EXTRA_SESSION_ID, sessionId)
            context.startService(intent)
        }
    }
}
