package uk.co.fireburn.lochssh.data

import android.content.ContextWrapper
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.fireburn.lochssh.ssh.RecordingMetadata
import uk.co.fireburn.lochssh.ssh.SshConnectionConfig
import uk.co.fireburn.lochssh.ssh.SshConnectionManager

class SessionRecordingsAndroidTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory = File(base.noBackupFilesDir, "recording-test-${UUID.randomUUID()}")
    private val context = object : ContextWrapper(base) {
        override fun getNoBackupFilesDir(): File = directory
    }

    @After fun cleanup() { directory.deleteRecursively() }

    @Test fun deletedRecordingDoesNotTruncateExportDestination(): Unit = runBlocking {
        assertTrue(directory.mkdirs())
        val destination = File(directory, "export.terminal").apply { writeText("Keep this file") }
        val entry = uk.co.fireburn.lochssh.ssh.RecordingFiles.Entry(UUID.randomUUID().toString(), 0, true)
        val failure = runCatching { SessionRecordings(context).export(entry, Uri.fromFile(destination)) }.exceptionOrNull()
        assertTrue(failure is java.io.IOException)
        assertEquals("Keep this file", destination.readText())
    }

    @Test fun exportsRawBytesWithoutDeletingPrivateOriginal(): Unit = runBlocking {
        val recordings = File(directory, "terminal-recordings")
        assertTrue(recordings.mkdirs())
        val id = UUID.randomUUID().toString()
        val source = File(recordings, "$id.terminal")
        val bytes = byteArrayOf(27, 91, 50, 74, -16, -97, -104, -128)
        source.writeBytes(bytes)
        val storage = SessionRecordings(context)
        val destination = File(directory, "export.terminal")
        storage.export(storage.list().single(), Uri.fromFile(destination))
        org.junit.Assert.assertArrayEquals(bytes, destination.readBytes())
        org.junit.Assert.assertArrayEquals(bytes, source.readBytes())
    }

    @Test fun listingDoesNotCreateRecordingFiles() = runBlocking {
        assertTrue(SessionRecordings(context).list().isEmpty())
        assertFalse(directory.exists())
    }

    @Test fun rejectedStartOnDisconnectedManagerDoesNotCreateFiles() = runBlocking {
        val config = SshConnectionConfig("localhost", 22, "test", "none", null, null, null, null, 30)
        val manager = SshConnectionManager(context, config, onExit = {})
        val failure = runCatching { SessionRecordings(context).start(manager, "Quark") }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertFalse(directory.exists())
    }

    @Test fun readsAndDeletesOnlyItsPrivateRecordingFiles() = runBlocking {
        val recordings = File(directory, "terminal-recordings")
        assertTrue(recordings.mkdirs())
        val id = UUID.randomUUID().toString()
        File(recordings, "$id.partial").writeBytes(byteArrayOf(1, 2, 3))
        RecordingMetadata("Quark", 1234).write(File(recordings, "$id.details"))
        val storage = SessionRecordings(context)
        val entry = storage.list().single()
        assertEquals("Quark", entry.hostLabel)
        assertEquals(3L, entry.size)
        assertFalse(entry.complete)
        storage.delete(entry)
        assertTrue(storage.list().isEmpty())
        assertEquals(0, recordings.listFiles()!!.size)
    }
}
