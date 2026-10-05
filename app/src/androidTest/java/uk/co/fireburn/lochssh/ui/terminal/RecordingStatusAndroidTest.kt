package uk.co.fireburn.lochssh.ui.terminal

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uk.co.fireburn.lochssh.ssh.RecordingCoordinator
import uk.co.fireburn.lochssh.ssh.RecordingWriter

class RecordingStatusAndroidTest {
    @get:Rule val ui = createComposeRule()

    @Test fun recordingShowsClosingAndFinalizationFailure() {
        val writer = RecordingWriter(ByteArrayOutputStream())
        val recording = RecordingCoordinator.Recording("test", writer)
        try {
            ui.setContent { RecordingStatus(recording, false, null) }
            ui.onNodeWithText("Recording").assertIsDisplayed()
            ui.runOnIdle { recording.stop() }
            ui.onNodeWithText("Stopping recording").assertIsDisplayed()
            assertTrue(writer.awaitClosed(3_000))
            ui.runOnIdle { recording.finished("Could not finish recording file") }
            ui.onNodeWithText("Could not finish recording file").assertIsDisplayed()
        } finally { writer.stop(); writer.awaitClosed(3_000) }
    }

    @Test fun anotherConnectionDoesNotInheritRecordingStatus() {
        val writer = RecordingWriter(ByteArrayOutputStream())
        val selected = mutableStateOf<RecordingCoordinator.Recording?>(RecordingCoordinator.Recording("test", writer))
        try {
            ui.setContent { RecordingStatus(selected.value, false, null) }
            ui.onNodeWithText("Recording").assertIsDisplayed()
            ui.runOnIdle { selected.value = null }
            ui.onNodeWithText("Recording").assertDoesNotExist()
            assertTrue(writer.state.value.accepting)
        } finally { writer.stop(); writer.awaitClosed(3_000) }
    }
}
