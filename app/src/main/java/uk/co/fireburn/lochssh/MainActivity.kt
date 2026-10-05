package uk.co.fireburn.lochssh

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.navigation.NavHostController
import javax.inject.Inject
import uk.co.fireburn.lochssh.data.AppPreferences
import uk.co.fireburn.lochssh.security.AppLockViewModel
import uk.co.fireburn.lochssh.security.DeviceUnlock
import uk.co.fireburn.lochssh.ui.security.AppLockGate
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import uk.co.fireburn.lochssh.service.SshForegroundService
import uk.co.fireburn.lochssh.ui.hosts.HostEditorScreen
import uk.co.fireburn.lochssh.ui.hosts.HostListScreen
import uk.co.fireburn.lochssh.ui.hosts.HostShortcuts
import uk.co.fireburn.lochssh.ui.hosts.HostShortcutScreen
import uk.co.fireburn.lochssh.ui.identities.IdentityEditorScreen
import uk.co.fireburn.lochssh.ui.identities.IdentityListScreen
import uk.co.fireburn.lochssh.ui.settings.SettingsScreen
import uk.co.fireburn.lochssh.ui.settings.ConnectionHistoryScreen
import uk.co.fireburn.lochssh.ui.settings.HostKeysScreen
import uk.co.fireburn.lochssh.ui.sftp.SftpScreen
import uk.co.fireburn.lochssh.ui.terminal.TerminalScreen
import uk.co.fireburn.lochssh.ui.terminal.switchTerminal
import uk.co.fireburn.lochssh.ui.terminal.openSessionNotification
import uk.co.fireburn.lochssh.ui.terminal.RecordingsScreen
import uk.co.fireburn.lochssh.ui.theme.LochSSHTheme

object Routes {
    const val HOSTS = "hosts"
    const val RECORDINGS = "recordings"
    const val HOST_SHORTCUT = "host_shortcut?hostId={hostId}"
    fun hostShortcut(id: Long) = "host_shortcut?hostId=$id"
    const val HOST_EDITOR = "host_editor?id={id}"
    fun hostEditor(id: Long) = "host_editor?id=$id"
    const val IDENTITIES = "identities"
    const val IDENTITY_EDITOR = "identity_editor?id={id}"
    fun identityEditor(id: Long) = "identity_editor?id=$id"
    const val SETTINGS = "settings"
    const val HOST_KEYS = "host_keys"
    const val HISTORY = "history"
    const val SFTP = "sftp?sessionId={sessionId}"
    fun sftp(sessionId: Long) = "sftp?sessionId=$sessionId"
    const val TERMINAL = "terminal?sessionId={sessionId}"
    fun terminal(sessionId: Long) = "terminal?sessionId=$sessionId"
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var preferences: AppPreferences
    private val lockModel: AppLockViewModel by viewModels()
    private val unlockDevice = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        lockModel.lock.result(it.resultCode == RESULT_OK)
    }

    // A session id arrives here when one of the connection notifications is tapped.
    private val resumeSession = MutableStateFlow<Long?>(null)
    private val shortcutHost = MutableStateFlow<Long?>(null)

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lockModel.lock.setEnabled(preferences.appLock.value)
        DeviceUnlock.protect(window, preferences.appLock.value)
        lifecycleScope.launch {
            preferences.appLock.collect {
                DeviceUnlock.protect(window, it)
                lockModel.lock.setEnabled(it)
            }
        }
        if (savedInstanceState == null) readLaunch(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            LochSSHTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    // The lock must update even while the activity is stopped.
                    val status by lockModel.lock.status.collectAsState()
                    val navController = rememberNavController()
                    AppLockGate(status, ::unlock, ::finish) {
                        LochSshNavHost(resumeSession, shortcutHost, navController)
                    }
                }
            }
        }
    }

    override fun onPause() {
        if (!isChangingConfigurations) lockModel.lock.background()
        super.onPause()
    }

    override fun onStop() {
        if (!isChangingConfigurations) lockModel.lock.background()
        super.onStop()
    }

    private fun unlock() {
        if (!lockModel.lock.beginUnlock()) return
        try { unlockDevice.launch(DeviceUnlock.request(this))
        } catch (e: Exception) { lockModel.lock.failed(e.message ?: "Could not open device unlock") }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readLaunch(intent)
    }

    private fun readLaunch(intent: Intent?) {
        shortcutHost.value = HostShortcuts.hostId(intent)
        resumeSession.value = if (shortcutHost.value == null) sessionOf(intent) else null
    }

    private fun sessionOf(intent: Intent?): Long? =
        intent?.getLongExtra(SshForegroundService.EXTRA_SESSION_ID, -1L)?.takeIf { it >= 0 }
}

@Composable
fun LochSshNavHost(resumeSession: MutableStateFlow<Long?>, shortcutHost: MutableStateFlow<Long?>,
    navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    LaunchedEffect(navController) {
        shortcutHost.collect { id ->
            if (id != null) {
                navController.navigate(Routes.hostShortcut(id)) {
                    popUpTo(Routes.HOSTS)
                    launchSingleTop = true
                }
                shortcutHost.value = null
            }
        }
    }

    LaunchedEffect(navController) {
        resumeSession.collect { id ->
            if (id != null) {
                navController.openSessionNotification(id)
                resumeSession.value = null
            }
        }
    }

    NavHost(navController = navController, startDestination = Routes.HOSTS) {
        composable(Routes.HOST_SHORTCUT,
            arguments = listOf(navArgument("hostId") { type = NavType.LongType })) { entry ->
            HostShortcutScreen(hostId = entry.arguments?.getLong("hostId") ?: -1L,
                onConnect = { hostId, sessionId ->
                    SshForegroundService.start(context, hostId, sessionId)
                    navController.switchTerminal(sessionId)
                }, onBack = { navController.popBackStack() })
        }
        composable(Routes.HOSTS) {
            HostListScreen(
                onNewSession = { hostId, sessionId ->
                    SshForegroundService.start(context, hostId, sessionId)
                    navController.navigate(Routes.terminal(sessionId))
                },
                // Resuming only opens the screen again; the session is already up.
                onResumeSession = { sessionId ->
                    navController.switchTerminal(sessionId)
                },
                onEdit = { id -> navController.navigate(Routes.hostEditor(id)) },
                onNew = { navController.navigate(Routes.hostEditor(0)) },
                onIdentities = { navController.navigate(Routes.IDENTITIES) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onRecordings = { navController.navigate(Routes.RECORDINGS) }
            )
        }
        composable(Routes.RECORDINGS) {
            RecordingsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            Routes.HOST_EDITOR,
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            HostEditorScreen(
                hostId = entry.arguments?.getLong("id") ?: 0L,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.IDENTITIES) {
            IdentityListScreen(
                onEdit = { id -> navController.navigate(Routes.identityEditor(id)) },
                onNew = { navController.navigate(Routes.identityEditor(0)) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            Routes.IDENTITY_EDITOR,
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            IdentityEditorScreen(
                identityId = entry.arguments?.getLong("id") ?: 0L,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onHostKeys = { navController.navigate(Routes.HOST_KEYS) },
                onHistory = { navController.navigate(Routes.HISTORY) }
            )
        }
        composable(Routes.HISTORY) {
            ConnectionHistoryScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.HOST_KEYS) {
            HostKeysScreen(onBack = { navController.popBackStack() })
        }
        composable(
            Routes.SFTP,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) { entry ->
            SftpScreen(
                sessionId = entry.arguments?.getLong("sessionId") ?: -1L,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            Routes.TERMINAL,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) { entry ->
            TerminalScreen(
                sessionId = entry.arguments?.getLong("sessionId") ?: -1L,
                onBack = { navController.popBackStack() },
                onSwitchSession = { navController.switchTerminal(it) },
                onHosts = { navController.popBackStack(Routes.HOSTS, false) },
                onFiles = { navController.navigate(Routes.sftp(entry.arguments?.getLong("sessionId") ?: -1L)) }
            )
        }
    }
}
