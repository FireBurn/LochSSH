package uk.co.fireburn.lochssh

import android.os.Bundle
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
    const val TERMINAL = "terminal?hostId={hostId}"
    fun terminal(hostId: Long) = "terminal?hostId=$hostId"
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LochSSHTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    LochSshNavHost()
                }
            }
        }
    }
}

@Composable
fun LochSshNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current

    NavHost(navController = navController, startDestination = Routes.HOSTS) {
        composable(Routes.HOSTS) {
            HostListScreen(
                onOpenTerminal = { id ->
                    SshForegroundService.start(context, id)
                    navController.navigate(Routes.terminal(id))
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
            arguments = listOf(navArgument("hostId") { type = NavType.LongType })
        ) { entry ->
            TerminalScreen(
                hostId = entry.arguments?.getLong("hostId") ?: -1L,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
