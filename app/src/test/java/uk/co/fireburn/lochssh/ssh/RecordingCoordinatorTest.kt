package uk.co.fireburn.lochssh.ssh

import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RecordingCoordinatorTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @After fun close() { scope.cancel() }

    @Test fun cancelledOwnerDoesNotCreateFiles() {
        val directory = folder.newFolder()
        scope.cancel()
        assertThrows(kotlinx.coroutines.CancellationException::class.java) {
            RecordingCoordinator(RecordingFiles(directory), scope).start(RecordingCapture(), "Quark")
        }
        assertEquals(0, directory.listFiles()!!.size)
    }

    @Test fun oldStopActionDoesNotStopANewerRecording() = runBlocking {
        val files = RecordingFiles(folder.newFolder())
        val capture = RecordingCapture()
        val coordinator = RecordingCoordinator(files, scope)
        val first = coordinator.start(capture, "Quark")
        first.stop()
        withTimeout(3_000) { first.completion.first { it != null } }
        val second = coordinator.start(capture, "Quark")
        first.stop()
        assertTrue(second.writer.state.value.accepting)
        capture.append(byteArrayOf(7), 1)
        second.stop()
        assertNull(withTimeout(3_000) { second.completion.first { it != null } }?.error)
        val output = ByteArrayOutputStream()
        files.export(files.list().first { it.id == second.id }, output)
        assertArrayEquals(byteArrayOf(7), output.toByteArray())
    }

    @Test fun connectionCloseFinalizesOutputWithoutAnotherUiAction() = runBlocking {
        val files = RecordingFiles(folder.newFolder())
        val capture = RecordingCapture()
        val recording = RecordingCoordinator(files, scope).start(capture, "Quark")
        capture.append(byteArrayOf(1, 2, 3), 3)
        capture.close()
        val result = withTimeout(3_000) { recording.completion.first { it != null } }
        assertNull(result?.error)
        val entry = files.list().single()
        assertTrue(entry.complete)
        assertEquals("Quark", entry.hostLabel)
        val output = ByteArrayOutputStream()
        files.export(entry, output)
        assertArrayEquals(byteArrayOf(1, 2, 3), output.toByteArray())
    }

    @Test fun closedConnectionDoesNotLeaveAnUnusedFile() {
        val directory = folder.newFolder()
        val files = RecordingFiles(directory)
        val capture = RecordingCapture()
        capture.close()
        assertThrows(IllegalStateException::class.java) {
            RecordingCoordinator(files, scope).start(capture, "Quark")
        }
        assertEquals(0, directory.listFiles()!!.size)
    }

    @Test fun duplicateStartKeepsTheOriginalRecordingAndDoesNotCreateAnotherFile() = runBlocking {
        val files = RecordingFiles(folder.newFolder())
        val capture = RecordingCapture()
        val coordinator = RecordingCoordinator(files, scope)
        val recording = coordinator.start(capture, "Quark")
        try {
            assertThrows(IllegalStateException::class.java) { coordinator.start(capture, "Other") }
            assertEquals(1, files.list().size)
        } finally { recording.stop() }
        assertNull(withTimeout(3_000) { recording.completion.first { it != null } }?.error)
        assertTrue(files.list().single().complete)
    }
}
