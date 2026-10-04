package uk.co.fireburn.lochssh.ui.hosts

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import uk.co.fireburn.lochssh.data.db.SshHostEntity

class HostRemoteSessionsAndroidTest {
    @get:Rule val compose = createComposeRule()

    @Test fun hostMenuOffersRemoteSessionDiscovery() {
        var discoveries = 0
        compose.setContent { MaterialTheme {
            HostRow(SshHostEntity(name = "Quark", host = "test.invalid"), {}, {}, {}, {}, {}, { discoveries++ })
        } }
        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithText("Find and connect all remote sessions").performClick()
        compose.runOnIdle { assertEquals(1, discoveries) }
    }

    @Test fun globalButtonCallsDiscoveryAndDisablesWithoutHosts() {
        var calls = 0
        val enabled = mutableStateOf(true)
        compose.setContent { MaterialTheme { HostRemoteSessionsButton(enabled.value) { calls++ } } }
        compose.onNodeWithText("Find and connect remote sessions on all hosts").performClick()
        compose.runOnIdle { assertEquals(1, calls); enabled.value = false }
        compose.onNodeWithText("Find and connect remote sessions on all hosts").assertIsNotEnabled()
    }
}
