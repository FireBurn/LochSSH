package uk.co.fireburn.lochssh.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SecretCleanupCardAndroidTest {
    @get:Rule
    val ui = createComposeRule()

    @Test
    fun showsTheFailureAndOffersRetry() {
        var retries = 0
        ui.setContent {
            MaterialTheme {
                SecretCleanupCard("Could not remove unused secrets", false) { retries++ }
            }
        }
        ui.onNodeWithText("Stored secret cleanup").assertIsDisplayed()
        ui.onNodeWithText("Could not remove unused secrets").assertIsDisplayed()
        ui.onNodeWithText("Retry cleanup").assertIsEnabled().performClick()
        ui.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun disablesRetryDuringCleanupAndRestoresItAfterwards() {
        val running = mutableStateOf(true)
        val error = mutableStateOf<String?>("Previous failure")
        var retries = 0
        ui.setContent {
            MaterialTheme { SecretCleanupCard(error.value, running.value) { retries++ } }
        }
        ui.onNodeWithText("Checking…").assertIsNotEnabled().performClick()
        ui.runOnIdle {
            assertEquals(0, retries)
            running.value = false
            error.value = null
        }
        ui.onNodeWithText("Previous failure").assertDoesNotExist()
        ui.onNodeWithText("Checking…").assertDoesNotExist()
        ui.onNodeWithText("Retry cleanup").assertIsEnabled().performClick()
        ui.runOnIdle { assertEquals(1, retries) }
    }
}
