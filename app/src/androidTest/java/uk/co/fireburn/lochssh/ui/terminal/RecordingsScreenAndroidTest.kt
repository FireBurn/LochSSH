package uk.co.fireburn.lochssh.ui.terminal

import android.content.ContextWrapper
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import uk.co.fireburn.lochssh.data.SessionRecordings
import uk.co.fireburn.lochssh.ssh.RecordingFiles

class RecordingsScreenAndroidTest {
    @get:Rule val ui = createComposeRule()
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory = File(base.noBackupFilesDir, "recording-screen-test-${UUID.randomUUID()}")
    private val context = object : ContextWrapper(base) {
        override fun getNoBackupFilesDir() = directory
    }
    private val owners = ViewModelStore()
    private val model = RecordingsViewModel(SessionRecordings(context))

    @After fun cleanup() {
        ui.runOnIdle { owners.clear() }
        directory.deleteRecursively()
    }

    @Test fun opensWithoutAHostOrConnectionAndCloses() {
        var backs = 0
        show { backs++ }
        ui.waitUntil(3_000) { ui.onAllNodes(hasText("No recordings")).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Close").performClick()
        ui.runOnIdle { assertEquals(1, backs) }
    }

    @Test fun cancelledExportClearsSelectionWithoutWritingDestination() {
        ui.runOnIdle {
            model.select(RecordingFiles.Entry(UUID.randomUUID().toString(), 0, true))
            model.export(null)
            assertNull(model.error.value)
            model.export(Uri.fromFile(File(directory, "unused.terminal")))
            assertEquals("Recording is no longer selected. Try exporting again.", model.error.value)
        }
        org.junit.Assert.assertFalse(directory.exists())
    }

    @Test fun unavailableRecordingShowsExportErrorWithoutTruncatingFile() {
        show {}
        directory.mkdirs()
        val destination = File(directory, "existing.terminal").apply { writeText("Keep this") }
        ui.runOnIdle {
            model.select(RecordingFiles.Entry(UUID.randomUUID().toString(), 0, true))
            model.export(Uri.fromFile(destination))
        }
        ui.waitUntil(3_000) { model.error.value != null }
        ui.onNodeWithText("Could not export recording").assertIsDisplayed()
        assertEquals("Keep this", destination.readText())
        ui.onNodeWithText("OK").performClick()
        ui.runOnIdle { assertNull(model.error.value) }
    }

    private fun show(onBack: () -> Unit) {
        ui.runOnIdle { owners.put("recordings", model) }
        ui.setContent { RecordingsScreen(onBack, model) }
    }
}
