package uk.co.fireburn.lochssh.ui.terminal

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Rule
import org.junit.Test
import uk.co.fireburn.lochssh.data.AppPreferences
import uk.co.fireburn.lochssh.data.db.LochSshDatabase
import uk.co.fireburn.lochssh.ssh.SessionRegistry

class TerminalRecoveryAndroidTest {
    @get:Rule val ui = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val database = Room.inMemoryDatabaseBuilder(context, LochSshDatabase::class.java).build()
    private val store = ViewModelStore()
    private val registry = SessionRegistry()

    @After fun close() {
        ui.runOnIdle { store.clear() }
        database.close()
    }

    private fun showTerminal() {
        val hosts = mutableStateOf(false)
        lateinit var model: TerminalViewModel
        ui.runOnUiThread {
            model = TerminalViewModel(AppPreferences(context), registry, database.savedCommandDao(), context,
                uk.co.fireburn.lochssh.data.SessionRecordings(context))
            store.put("terminal", model)
        }
        ui.setContent {
            MaterialTheme {
                if (hosts.value) Text("Host list")
                else TerminalScreen(11, { hosts.value = true }, {}, {}, { hosts.value = true }, model)
            }
        }
    }

    @Test fun missingConnectionReturnsToHosts() {
        showTerminal()
        ui.waitUntil(5_000) { ui.onAllNodesWithText("Host list").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Host list").assertIsDisplayed()
    }

    @Test fun droppedConnectionOffersReconnectAndHosts() {
        registry.opening(11, 1, "Quark", "tmux attach-session -t '\$0'")
        registry.failed(11, "Connection lost")
        showTerminal()
        ui.onNodeWithText("Reconnect").assertIsDisplayed()
        ui.onNode(hasText("Manage hosts") and
            SemanticsMatcher("On screen") { it.boundsInRoot.right > 0 }).performClick()
        ui.onNodeWithText("Host list").assertIsDisplayed()
    }
}
