package uk.co.fireburn.lochssh.ui.terminal

import android.content.ContextWrapper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uk.co.fireburn.lochssh.data.SessionRecordings
import uk.co.fireburn.lochssh.ssh.RecordingFiles
import uk.co.fireburn.lochssh.ssh.RecordingMetadata

class RecordingsDialogAndroidTest {
    @get:Rule val ui = createComposeRule()
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory = File(base.noBackupFilesDir, "recording-ui-test-${UUID.randomUUID()}")
    private val context = object : ContextWrapper(base) {
        override fun getNoBackupFilesDir() = directory
    }
    @After fun cleanup() { directory.deleteRecursively() }

    @Test fun listsIncompleteOutputAndPassesSelectedEntryToExport() {
        val id = createRecording()
        var selected: RecordingFiles.Entry? = null
        val store = SessionRecordings(context)
        ui.setContent { RecordingsDialog(store, { selected = it }, {}) }
        ui.waitUntil(3_000) { ui.onAllNodes(androidx.compose.ui.test.hasText("Quark")).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("3 bytes · Incomplete").assertIsDisplayed()
        ui.onNodeWithText("Export").performClick()
        ui.runOnIdle { assertEquals(id, selected?.id) }
    }

    @Test fun cancellationKeepsRecordingAndConfirmedDeleteRemovesIt() {
        val id = createRecording()
        val store = SessionRecordings(context)
        ui.setContent { RecordingsDialog(store, {}, {}) }
        ui.waitUntil(3_000) { ui.onAllNodes(androidx.compose.ui.test.hasText("Quark")).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Delete").performClick()
        ui.onNodeWithText("Cancel").performClick()
        assertTrue(File(directory, "terminal-recordings/$id.partial").exists())
        ui.onNodeWithText("Delete").performClick()
        ui.onNodeWithText("Delete recording?").assertIsDisplayed()
        ui.onNodeWithTag("confirm-recording-delete").performClick()
        ui.waitUntil(3_000) { !File(directory, "terminal-recordings/$id.partial").exists() }
        ui.onNodeWithText("No recordings").assertIsDisplayed()
    }

    private fun createRecording(): String {
        val recordings = File(directory, "terminal-recordings")
        assertTrue(recordings.mkdirs())
        val id = UUID.randomUUID().toString()
        File(recordings, "$id.partial").writeBytes(byteArrayOf(1, 2, 3))
        RecordingMetadata("Quark", 1234).write(File(recordings, "$id.details"))
        return id
    }
}
