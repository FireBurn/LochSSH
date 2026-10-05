package uk.co.fireburn.lochssh.ssh

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RecordingMetadataTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun preservesUnicodeLabelsAndStartTimes() {
        val file = File(folder.root, "details")
        val details = RecordingMetadata("Quark · 编译 😀", 1234)
        details.write(file)
        assertEquals(details, RecordingMetadata.read(file))
        assertThrows(IllegalStateException::class.java) { details.write(file) }
        assertEquals(details, RecordingMetadata.read(file))
    }

    @Test fun rejectsMissingMalformedAndOversizedDetails() {
        assertNull(RecordingMetadata.read(File(folder.root, "missing")))
        val malformed = folder.newFile()
        malformed.writeBytes(byteArrayOf(1, 2, 3))
        assertNull(RecordingMetadata.read(malformed))
        malformed.writeBytes(ByteArray(2049))
        assertNull(RecordingMetadata.read(malformed))
        assertThrows(IllegalArgumentException::class.java) {
            RecordingMetadata("a".repeat(513), 1234).write(File(folder.root, "too-long"))
        }
    }

    @Test fun doesNotReadSymbolicLinks() {
        val source = File(folder.root, "source")
        RecordingMetadata("Quark", 1234).write(source)
        val link = File(folder.root, "link")
        Files.createSymbolicLink(link.toPath(), source.toPath())
        assertNull(RecordingMetadata.read(link))
    }
}
