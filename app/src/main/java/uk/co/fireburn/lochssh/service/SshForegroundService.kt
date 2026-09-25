package uk.co.fireburn.lochssh.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
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
import uk.co.fireburn.lochssh.data.db.AuthTypes
import uk.co.fireburn.lochssh.data.db.IdentityDao
import uk.co.fireburn.lochssh.data.db.PortForwardDao
import uk.co.fireburn.lochssh.data.db.SshHostDao
import uk.co.fireburn.lochssh.ssh.PortForwardSpec
import uk.co.fireburn.lochssh.ssh.SessionRegistry
import uk.co.fireburn.lochssh.ssh.SshConnectionConfig
import uk.co.fireburn.lochssh.ssh.SshConnectionManager
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class SshForegroundService : Service() {

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

    private val managers = ConcurrentHashMap<Long, SshConnectionManager>()
    private var wakeLock: PowerManager.WakeLock? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
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
        scope.launch { connect(sessionId, hostId) }
        return Service.START_NOT_STICKY
    }

    private suspend fun connect(sessionId: Long, hostId: Long) {
        val host = hostDao.getById(hostId) ?: return fail(sessionId, "Host not found")
        registry.opening(sessionId, hostId, host.name)
        // Key loading and the SSH handshake block, so keep them off the main thread.
        withContext(Dispatchers.IO) {
            val identity = host.identityId?.let { identityDao.getById(it) }
            val secret = identity?.secretRef?.let { secrets.getSecret(it) }
            val keyMaterial = identity?.keyMaterialRef?.let { secrets.getSecret(it) }

            val config = SshConnectionConfig(
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
                forwards = portForwardDao.getByHost(hostId).map {
                    PortForwardSpec(it.type, it.localPort, it.remoteHost, it.remotePort)
                }
            )

            val manager = SshConnectionManager(
                context = this@SshForegroundService,
                config = config,
                onTitle = { title -> scope.launch { onSessionTitle(sessionId, title) } }
            ) { code ->
                scope.launch { onSessionExit(sessionId, code) }
            }
            try {
                manager.connect()
            } catch (e: Exception) {
                Log.e(TAG, "Connect to ${host.host}:${host.port} as ${config.username} failed", e)
                manager.disconnect()
                fail(sessionId, e.message ?: e.javaClass.simpleName)
                return@withContext
            }
            managers[sessionId] = manager
            registry.connected(sessionId, manager)
            showSessionNotification(sessionId, host.name, "Connected to ${host.host}")
            updateSummary()
        }
    }

    private fun onSessionTitle(sessionId: Long, title: String) {
        registry.titled(sessionId, title)
        val session = registry.find(sessionId) ?: return
        if (managers.containsKey(sessionId)) {
            showSessionNotification(sessionId, session.label, "Connected to ${session.hostName}")
        }
    }

    private fun onSessionExit(sessionId: Long, code: Int) {
        Log.i(TAG, "Session $sessionId exited with code $code")
        endSession(sessionId)
    }

    private fun endSession(sessionId: Long) {
        managers.remove(sessionId)?.disconnect()
        registry.remove(sessionId)
        NotificationManagerCompat.from(this).cancel(sessionNotificationId(sessionId))
        stopIfIdle()
    }

    private fun fail(sessionId: Long, message: String) {
        Log.e(TAG, "Session $sessionId failed: $message")
        registry.failed(sessionId, "Connection failed: $message")
        NotificationManagerCompat.from(this).cancel(sessionNotificationId(sessionId))
        stopIfIdle()
    }

    private fun stopIfIdle() {
        if (managers.isEmpty()) {
            stopSelf()
        } else {
            updateSummary()
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
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(SUMMARY_NOTIFICATION_ID, summaryNotification())
        }
    }

    private fun updateSummary() {
        notifyIfAllowed(SUMMARY_NOTIFICATION_ID, summaryNotification())
    }

    // Keeps the CPU up so connections survive Doze while the user is attached.
    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .apply { acquire(WAKE_LOCK_TIMEOUT_MS) }
    }

    override fun onDestroy() {
        managers.values.forEach { it.disconnect() }
        managers.keys.forEach { registry.remove(it) }
        managers.clear()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_SESSION_ID = "session_id"

        private const val EXTRA_HOST_ID = "host_id"
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

        fun start(context: Context, hostId: Long, sessionId: Long) {
            val intent = Intent(context, SshForegroundService::class.java)
                .putExtra(EXTRA_HOST_ID, hostId)
                .putExtra(EXTRA_SESSION_ID, sessionId)
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
