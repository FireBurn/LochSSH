package uk.co.fireburn.lochssh.ui.security

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uk.co.fireburn.lochssh.security.DeviceUnlock
import uk.co.fireburn.lochssh.security.LockStatus

class AppLockGateAndroidTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()

    @Test fun doesNotComposeProtectedContentWhileLocked() {
        var protectedCalls = 0
        var requested = 0
        var closed = 0
        ui.setContent { MaterialTheme {
            AppLockGate(LockStatus(true, true), { requested++ }, { closed++ }) {
                protectedCalls++
                Text("Private terminal")
            }
        } }
        ui.onNodeWithText("Private terminal").assertDoesNotExist()
        ui.onNodeWithText("Unlock").performClick()
        ui.onNodeWithText("LochSSH is locked").assertIsDisplayed()
        ui.onNodeWithText("Close").performClick()
        ui.runOnIdle { assertEquals(0, protectedCalls); assertEquals(1, requested); assertEquals(1, closed) }
    }

    @Test fun keepsTheTerminalRouteAcrossLocking() {
        val status = mutableStateOf(LockStatus(true))
        ui.setContent { MaterialTheme {
            val navigation = rememberNavController()
            AppLockGate(status.value, { status.value = LockStatus(true) }, {}) {
                NavHost(navigation, "hosts") {
                    composable("hosts") {
                        TextButton(onClick = { navigation.navigate("terminal") }) { Text("Open terminal") }
                    }
                    composable("terminal") {
                        TextButton(onClick = { status.value = LockStatus(true, true) }) { Text("Private terminal") }
                    }
                }
            }
        } }
        ui.onNodeWithText("Open terminal").performClick()
        ui.onNodeWithText("Private terminal").performClick()
        ui.onNodeWithText("Private terminal").assertDoesNotExist()
        ui.onNodeWithText("Unlock").performClick()
        ui.onNodeWithText("Private terminal").assertIsDisplayed()
    }

    @Test fun pendingDeviceUnlockCannotBeRequestedTwice() {
        ui.setContent { MaterialTheme { AppLockGate(LockStatus(true, true, true), {}, {}) { Text("Private") } } }
        ui.onNodeWithText("Waiting for device unlock…").assertIsNotEnabled()
        ui.onNodeWithText("Private").assertDoesNotExist()
    }

    @Test fun protectsAndRestoresTheWindowScreenshotFlag() {
        ui.runOnUiThread {
            val window = ui.activity.window
            val original = window.attributes.flags
            try {
                DeviceUnlock.protect(window, true)
                assertTrue(window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                DeviceUnlock.protect(window, false)
                assertEquals(0, window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
            } finally { window.attributes.flags = original }
        }
    }
}
