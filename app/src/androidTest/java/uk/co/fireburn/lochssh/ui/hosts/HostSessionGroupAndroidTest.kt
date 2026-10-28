package uk.co.fireburn.lochssh.ui.hosts

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import uk.co.fireburn.lochssh.ssh.SessionRegistry

class HostSessionGroupAndroidTest {
    @get:Rule val compose = createComposeRule()

    @Test fun showsTheHostAndAddressAndResumesTheSelectedSession() {
        var selected = 0L
        val group = HostSessionGroup(7, "Quark", "quark.test:22",
            listOf(SessionRegistry.Session(3, 7, "Quark", remoteName = "claude")))
        compose.setContent { MaterialTheme { HostSessionGroupView(group, { selected = it }, {}) } }
        compose.onNodeWithText("Quark").assertExists()
        compose.onNodeWithText("quark.test:22").assertExists()
        compose.onNodeWithText("claude").performClick()
        compose.runOnIdle { assertEquals(3L, selected) }
    }

    @Test fun dismissesTheCorrectDroppedSession() {
        var dismissed = 0L
        val group = HostSessionGroup(7, "Quark", null,
            listOf(SessionRegistry.Session(9, 7, "Quark", error = "Dropped", remoteName = "build")))
        compose.setContent { MaterialTheme { HostSessionGroupView(group, {}, { dismissed = it }) } }
        compose.onNodeWithText("Dropped").assertExists()
        compose.onNodeWithContentDescription("Dismiss").performClick()
        compose.runOnIdle { assertEquals(9L, dismissed) }
    }

    @Test fun tmuxButtonsAppearOnlyForHostsThatCanRunTmux() {
        val sessions = listOf(SessionRegistry.Session(3, 7, "Quark"))
        val ssh = HostSessionGroup(7, "Quark", null, sessions)
        val local = HostSessionGroup(7, "Phone", null, sessions, hasTmux = false)
        val shown = androidx.compose.runtime.mutableStateOf(ssh)
        compose.setContent { MaterialTheme { HostSessionGroupView(shown.value, {}, {}) } }
        compose.onNodeWithText("New tmux").assertExists()
        compose.onNodeWithText("Rescan").assertExists()
        compose.runOnIdle { shown.value = local }
        compose.onNodeWithText("New tmux").assertDoesNotExist()
        compose.onNodeWithText("Rescan").assertDoesNotExist()
    }
}
