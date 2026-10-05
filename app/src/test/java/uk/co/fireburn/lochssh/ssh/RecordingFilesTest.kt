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

    @Test fun unavailableOrActiveRecordingDoesNotOpenDestination() {
        val files = RecordingFiles(folder.newFolder())
        var opened = false
        val open = { opened = true; ByteArrayOutputStream() }
        assertThrows(java.io.IOException::class.java) {
            files.export(RecordingFiles.Entry("00000000-0000-0000-0000-000000000001", 0, false), open)
        }
        val (_, writer) = files.create()
        try {
            assertThrows(IllegalStateException::class.java) { files.export(files.list().single(), open) }
            assertFalse(opened)
        } finally { writer.stop(); assertTrue(writer.awaitClosed(3_000)) }
    }

    @Test fun ownedExportClosesDestinationAndPreservesOriginal() {
        val files = RecordingFiles(folder.newFolder())
        val (id, writer) = files.create()
        writer.append(byteArrayOf(1, 2, 3), 3)
        writer.stop()
        assertTrue(writer.awaitClosed(3_000))
        files.finish(id)
        var closed = false
        val output = object : ByteArrayOutputStream() {
            override fun close() { closed = true; super.close() }
        }
        val entry = files.list().single()
        files.export(entry) { output }
        assertTrue(closed)
        assertArrayEquals(byteArrayOf(1, 2, 3), output.toByteArray())
        assertEquals(entry, files.list().single())
    }

    @Test fun failedExportLeavesOriginalBytesAndDetailsUnchanged() {
        val directory = folder.newFolder()
        val files = RecordingFiles(directory)
        val (id, writer) = files.create("Quark", 1234)
        val bytes = byteArrayOf(1, 2, 3)
        writer.append(bytes, bytes.size)
        writer.stop()
        assertTrue(writer.awaitClosed(3_000))
        files.finish(id)
        val entry = files.list().single()
        val failedOutput = object : java.io.OutputStream() {
            override fun write(value: Int) { throw java.io.IOException("Unavailable destination") }
        }
        assertThrows(java.io.IOException::class.java) { files.export(entry, failedOutput) }
        assertEquals(entry, files.list().single())
        val output = ByteArrayOutputStream()
        files.export(entry, output)
        assertArrayEquals(bytes, output.toByteArray())
        assertEquals(RecordingMetadata("Quark", 1234), RecordingMetadata.read(File(directory, "$id.details")))
    }

    @Test fun failedCreationDoesNotDeleteExistingDetails() {
        val directory = folder.newFolder()
        val id = "00000000-0000-0000-0000-000000000001"
        val details = RecordingMetadata("Existing host", 1234)
        details.write(File(directory, "$id.details"))
        val files = RecordingFiles(directory) { id }
        assertThrows(IllegalStateException::class.java) { files.create("New host", 5678) }
        assertEquals(details, RecordingMetadata.read(File(directory, "$id.details")))
        assertFalse(File(directory, "$id.partial").exists())
    }

    @Test fun clearRemovesOrphanDetailsButKeepsUnrelatedFiles() {
        val directory = folder.newFolder()
        val id = "00000000-0000-0000-0000-000000000001"
        val details = File(directory, "$id.details")
        RecordingMetadata("Private host", 1234).write(details)
        val unrelated = File(directory, "other.details").apply { createNewFile() }
        RecordingFiles(directory).clear()
        assertFalse(details.exists())
        assertTrue(unrelated.exists())
    }

    @Test fun clearRejectsLinkedDetailsWithoutDeletingTheirTarget() {
        val directory = folder.newFolder()
        val outside = folder.newFile()
        val details = File(directory, "00000000-0000-0000-0000-000000000001.details")
        Files.createSymbolicLink(details.toPath(), outside.toPath())
        assertThrows(java.io.IOException::class.java) { RecordingFiles(directory).clear() }
        assertTrue(outside.exists())
        assertTrue(Files.isSymbolicLink(details.toPath()))
    }

    @Test fun clearRefusesActiveWritersBeforeRemovingDetails() {
        val directory = folder.newFolder()
        val files = RecordingFiles(directory)
        val (id, writer) = files.create()
        try {
            assertThrows(IllegalStateException::class.java) { files.clear() }
            assertTrue(File(directory, "$id.details").exists())
            assertTrue(File(directory, "$id.partial").exists())
        } finally { writer.stop(); assertTrue(writer.awaitClosed(3_000)) }
        files.clear()
        assertEquals(0, directory.listFiles()!!.size)
    }

    @Test fun finishedRecordingCanBeReopenedExportedAndDeleted() {
        val directory = folder.newFolder()
        val files = RecordingFiles(directory)
        val (id, writer) = files.create("Quark · 编译", 1234)
        writer.append(byteArrayOf(1, 2, 3), 3)
        writer.stop()
        assertTrue(writer.awaitClosed(3_000))
        files.finish(id)
        val reopened = RecordingFiles(directory)
        val entry = reopened.list().single()
        assertEquals(RecordingFiles.Entry(id, 3, true, "Quark · 编译", 1234), entry)
        val output = ByteArrayOutputStream()
        reopened.export(entry, output)
        assertArrayEquals(byteArrayOf(1, 2, 3), output.toByteArray())
        reopened.delete(entry)
        assertTrue(reopened.list().isEmpty())
        assertEquals(0, directory.listFiles()!!.size)
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
