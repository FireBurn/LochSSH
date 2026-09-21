package uk.co.fireburn.lochssh

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
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
import uk.co.fireburn.lochssh.ui.identities.IdentityEditorScreen
import uk.co.fireburn.lochssh.ui.identities.IdentityListScreen
import uk.co.fireburn.lochssh.ui.settings.SettingsScreen
import uk.co.fireburn.lochssh.ui.terminal.TerminalScreen
import uk.co.fireburn.lochssh.ui.theme.LochSSHTheme

object Routes {
    const val HOSTS = "hosts"
    const val HOST_EDITOR = "host_editor?id={id}"
    fun hostEditor(id: Long) = "host_editor?id=$id"
    const val IDENTITIES = "identities"
    const val IDENTITY_EDITOR = "identity_editor?id={id}"
    fun identityEditor(id: Long) = "identity_editor?id=$id"
    const val SETTINGS = "settings"
    const val TERMINAL = "terminal?sessionId={sessionId}"
    fun terminal(sessionId: Long) = "terminal?sessionId=$sessionId"
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // A session id arrives here when one of the connection notifications is tapped.
    private val resumeSession = MutableStateFlow<Long?>(null)

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resumeSession.value = sessionOf(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            LochSSHTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    LochSshNavHost(resumeSession)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        resumeSession.value = sessionOf(intent)
    }

    private fun sessionOf(intent: Intent?): Long? =
        intent?.getLongExtra(SshForegroundService.EXTRA_SESSION_ID, -1L)?.takeIf { it > 0 }
}

@Composable
fun LochSshNavHost(resumeSession: MutableStateFlow<Long?>) {
    val navController = rememberNavController()
    val context = LocalContext.current

    LaunchedEffect(navController) {
        resumeSession.collect { id ->
            if (id != null) {
                navController.navigate(Routes.terminal(id))
                resumeSession.value = null
            }
        }
    }

    NavHost(navController = navController, startDestination = Routes.HOSTS) {
        composable(Routes.HOSTS) {
            HostListScreen(
                onNewSession = { hostId, sessionId ->
                    SshForegroundService.start(context, hostId, sessionId)
                    navController.navigate(Routes.terminal(sessionId))
                },
                // Resuming only opens the screen again; the session is already up.
                onResumeSession = { sessionId ->
                    navController.navigate(Routes.terminal(sessionId))
                },
                onEdit = { id -> navController.navigate(Routes.hostEditor(id)) },
                onNew = { navController.navigate(Routes.hostEditor(0)) },
                onIdentities = { navController.navigate(Routes.IDENTITIES) },
                onSettings = { navController.navigate(Routes.SETTINGS) }
            )
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
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            Routes.TERMINAL,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) { entry ->
            TerminalScreen(
                sessionId = entry.arguments?.getLong("sessionId") ?: -1L,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
