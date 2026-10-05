package uk.co.fireburn.lochssh.ssh

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RecordingFilesTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun finishedRecordingCanBeReopenedExportedAndDeleted() {
        val directory = folder.newFolder()
        val files = RecordingFiles(directory)
        val (id, writer) = files.create()
        writer.append(byteArrayOf(1, 2, 3), 3)
        writer.stop()
        assertTrue(writer.awaitClosed(3_000))
        files.finish(id)
        val reopened = RecordingFiles(directory)
        val entry = reopened.list().single()
        assertEquals(RecordingFiles.Entry(id, 3, true), entry)
        val output = ByteArrayOutputStream()
        reopened.export(entry, output)
        assertArrayEquals(byteArrayOf(1, 2, 3), output.toByteArray())
        reopened.delete(entry)
        assertTrue(reopened.list().isEmpty())
    }

    @Test fun unfinishedFilesRemainIncompleteAndActiveFilesCannotBeRemoved() {
        val directory = folder.newFolder()
        val files = RecordingFiles(directory)
        val (_, writer) = files.create()
        try {
            val entry = files.list().single()
            assertFalse(entry.complete)
            assertThrows(IllegalStateException::class.java) { files.delete(entry) }
            assertThrows(IllegalStateException::class.java) { files.export(entry, ByteArrayOutputStream()) }
        } finally { writer.stop(); assertTrue(writer.awaitClosed(3_000)) }
        assertFalse(RecordingFiles(directory).list().single().complete)
    }

    @Test fun rejectsPathTraversalAndSymbolicLinks() {
        val directory = folder.newFolder()
        val files = RecordingFiles(directory)
        val outside = folder.newFile()
        val id = "00000000-0000-0000-0000-000000000001"
        Files.createSymbolicLink(File(directory, "$id.partial").toPath(), outside.toPath())
        assertTrue(files.list().isEmpty())
        assertThrows(java.io.IOException::class.java) { files.delete(RecordingFiles.Entry(id, 0, false)) }
        assertThrows(IllegalArgumentException::class.java) { files.export(RecordingFiles.Entry("../outside", 0, false), ByteArrayOutputStream()) }
        assertTrue(outside.exists())
    }
}
