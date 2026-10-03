package uk.co.fireburn.lochssh.ui.identities

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uk.co.fireburn.lochssh.ssh.GeneratedKey
import uk.co.fireburn.lochssh.ssh.KeyInstaller
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class KeyInstallDialogAndroidTest {
    @get:Rule val ui = createComposeRule()
    private val key = GeneratedKey("private", "public")

    private fun enterDetails() {
        ui.onNodeWithText("Server address").performTextInput("example.test")
        ui.onNodeWithText("Server password (not saved)").performTextInput("one-time")
        ui.onNodeWithText("Install key").performClick()
    }

    @Test fun usesThePasswordOnceAndClearsItsBytes() {
        var captured: ByteArray? = null
        val installer = object : KeyInstaller {
            override fun install(host: String, port: Int, username: String, password: ByteArray, key: GeneratedKey) {
                assertEquals("example.test", host); assertEquals(22, port); assertEquals("mike", username)
                assertEquals("one-time", password.toString(Charsets.UTF_8))
                captured = password
            }
            override fun close() { }
        }
        ui.setContent { MaterialTheme { KeyInstallDialog(key, "mike", {}, { installer }) } }
        enterDetails()
        ui.waitUntil(5_000) { ui.onAllNodesWithText("Key installed").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Key installed").assertIsDisplayed()
        assertArrayEquals(ByteArray(8), captured)
    }

    @Test fun requiresANewPasswordAfterFailure() {
        val installer = object : KeyInstaller {
            override fun install(host: String, port: Int, username: String, password: ByteArray, key: GeneratedKey) {
                throw IOException("Wrong password")
            }
            override fun close() { }
        }
        ui.setContent { MaterialTheme { KeyInstallDialog(key, "mike", {}, { installer }) } }
        enterDetails()
        ui.waitUntil(5_000) { ui.onAllNodesWithText("Wrong password").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Wrong password").assertIsDisplayed()
        ui.onNodeWithText("Install key").assertExists()
        ui.onNodeWithText("one-time").assertDoesNotExist()
    }

    @Test fun cancellingClosesTheConnectionAndClearsThePassword() {
        val open = mutableStateOf(true)
        val started = CountDownLatch(1)
        val stop = CountDownLatch(1)
        val closed = AtomicBoolean()
        var captured: ByteArray? = null
        val installer = object : KeyInstaller {
            override fun install(host: String, port: Int, username: String, password: ByteArray, key: GeneratedKey) {
                captured = password
                started.countDown()
                stop.await(10, TimeUnit.SECONDS)
            }
            override fun close() { closed.set(true); stop.countDown() }
        }
        ui.setContent { MaterialTheme {
            if (open.value) KeyInstallDialog(key, "mike", { open.value = false }, { installer }) else Text("Closed")
        } }
        enterDetails()
        ui.waitUntil(5_000) { started.count == 0L }
        ui.onNodeWithText("Cancel").performClick()
        ui.onNodeWithText("Closed").assertIsDisplayed()
        ui.waitUntil(5_000) { captured?.all { it == 0.toByte() } == true }
        assertTrue(closed.get())
    }
}
