package uk.co.fireburn.lochssh.ui.terminal

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uk.co.fireburn.lochssh.Routes
import uk.co.fireburn.lochssh.ssh.SessionRegistry

class SessionDrawerAndroidTest {
    @get:Rule
    val ui = createComposeRule()

    private val sessions = listOf(
        SessionRegistry.Session(11, 1, "Alpha"),
        SessionRegistry.Session(12, 2, "Beta", error = "Connection refused")
    )

    @Test
    fun showsStatusAndSelectsAnExistingSession() {
        var selected: Long? = null
        ui.setContent {
            MaterialTheme {
                SessionDrawer(sessions, 11, { selected = it }, {}) { open ->
                    TextButton(onClick = open) { Text("Switch session") }
                }
            }
        }
        ui.onNodeWithText("Switch session").performClick()
        ui.onNodeWithText("Alpha").assertIsSelected()
        ui.onNodeWithText("Connecting").assertIsDisplayed()
        ui.onNodeWithText("Connection refused").assertIsDisplayed()
        ui.onNodeWithText("Beta").performClick()
        ui.runOnIdle { assertEquals(12L, selected) }
    }

    @Test
    fun selectingCurrentSessionDoesNotNavigateAndRemovedRowsDisappear() {
        val rows = mutableStateOf(sessions)
        var selections = 0
        var hosts = 0
        ui.setContent {
            MaterialTheme {
                SessionDrawer(rows.value, 11, { selections++ }, { hosts++ }) { open ->
                    TextButton(onClick = open) { Text("Switch session") }
                }
            }
        }
        ui.onNodeWithText("Switch session").performClick()
        ui.onNodeWithText("Alpha").performClick()
        ui.runOnIdle { assertEquals(0, selections) }
        ui.onNodeWithText("Switch session").performClick()
        ui.runOnIdle { rows.value = sessions.take(1) }
        ui.onNodeWithText("Beta").assertDoesNotExist()
        ui.onNodeWithText("Manage hosts").performClick()
        ui.runOnIdle { assertEquals(1, hosts) }
    }

    @Test
    fun repeatedSwitchesLeaveOneTerminalAboveTheHostList() {
        lateinit var controller: NavHostController
        ui.setContent {
            MaterialTheme {
                val nav = rememberNavController()
                controller = nav
                NavHost(nav, startDestination = Routes.HOSTS) {
                    composable(Routes.HOSTS) {
                        TextButton(onClick = { nav.navigate(Routes.terminal(11)) }) { Text("Open terminal") }
                    }
                    composable(Routes.TERMINAL,
                        arguments = listOf(navArgument("sessionId") { type = NavType.LongType })) { entry ->
                        val id = requireNotNull(entry.arguments).getLong("sessionId")
                        SessionDrawer(sessions, id, { nav.switchTerminal(it) }, { nav.popBackStack() }) { open ->
                            TextButton(onClick = open) { Text("Switch session $id") }
                        }
                    }
                }
            }
        }
        ui.onNodeWithText("Open terminal").performClick()
        repeat(3) {
            ui.onNodeWithText("Switch session 11").performClick()
            ui.onNodeWithText("Beta").performClick()
            ui.onNodeWithText("Switch session 12").performClick()
            ui.onNodeWithText("Alpha").performClick()
        }
        ui.runOnIdle {
            assertEquals(Routes.HOSTS, controller.previousBackStackEntry?.destination?.route)
            assertTrue(controller.popBackStack())
        }
        ui.onNodeWithText("Open terminal").assertIsDisplayed()
    }
}
