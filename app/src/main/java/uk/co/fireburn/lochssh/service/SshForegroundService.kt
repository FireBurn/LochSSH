package uk.co.fireburn.lochssh.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import uk.co.fireburn.lochssh.R
import uk.co.fireburn.lochssh.data.EncryptedStorageManager
import uk.co.fireburn.lochssh.data.db.AuthTypes
import uk.co.fireburn.lochssh.data.db.IdentityDao
import uk.co.fireburn.lochssh.data.db.PortForwardDao
import uk.co.fireburn.lochssh.data.db.SshHostDao
import uk.co.fireburn.lochssh.ssh.ActiveConnection
import uk.co.fireburn.lochssh.ssh.PortForwardSpec
import uk.co.fireburn.lochssh.ssh.SshConnectionConfig
import uk.co.fireburn.lochssh.ssh.SshConnectionManager
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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

    private var manager: SshConnectionManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val hostId = intent?.getLongExtra(EXTRA_HOST_ID, -1L) ?: -1L
        if (hostId == -1L) {
            stopSelf()
            return Service.START_NOT_STICKY
        }
        scope.launch { connect(hostId) }
        return Service.START_NOT_STICKY
    }

    private suspend fun connect(hostId: Long) {
        val host = hostDao.getById(hostId) ?: return stopWith("Connection failed", "Host not found")
        val identity = host.identityId?.let { identityDao.getById(it) }
        val secret = identity?.secretRef?.let { secrets.getSecret(it) }

        val config = SshConnectionConfig(
            host = host.host,
            port = host.port,
            username = host.username,
            authType = identity?.authType ?: AuthTypes.NONE,
            password = if (identity?.authType == AuthTypes.PASSWORD) secret else null,
            keyPath = identity?.keyPath,
            keyPassphrase = if (identity?.authType == AuthTypes.PUBLIC_KEY) secret else null,
            keepAliveSeconds = host.keepAliveSeconds,
            forwards = portForwardDao.getByHost(hostId).map {
                PortForwardSpec(it.type, it.localPort, it.remoteHost, it.remotePort)
            }
        )

        val m = SshConnectionManager(config) { code ->
            scope.launch { onSessionExit(code) }
        }
        try {
            m.connect()
        } catch (e: Exception) {
            stopWith("Connection failed", e.message ?: "Unknown error")
            return
        }
        manager = m
        ActiveConnection.manager = m
        showNotification("LochSSH", "Connected to ${host.host}")
    }

    private fun onSessionExit(code: Int) {
        manager?.disconnect()
        manager = null
        ActiveConnection.manager = null
        showNotification("Connection closed", "Exit code $code")
        stopSelf()
    }

    private fun stopWith(title: String, text: String) {
        showNotification(title, text)
        stopSelf()
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

    private fun showNotification(title: String, text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nessi_placeholder)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // Keeps the CPU up so the connection survives Doze while the user is attached.
    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .apply { acquire(WAKE_LOCK_TIMEOUT_MS) }
    }

    override fun onDestroy() {
        manager?.disconnect()
        manager = null
        ActiveConnection.manager = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val EXTRA_HOST_ID = "host_id"
        private const val CHANNEL_ID = "ssh_connections"
        private const val NOTIFICATION_ID = 1
        private const val WAKE_LOCK_TAG = "lochssh:ssh-service"
        private const val WAKE_LOCK_TIMEOUT_MS = 12L * 60 * 60 * 1000

        fun start(context: Context, hostId: Long) {
            val intent = Intent(context, SshForegroundService::class.java)
                .putExtra(EXTRA_HOST_ID, hostId)
            context.startForegroundService(intent)
        }
    }
}
