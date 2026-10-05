package uk.co.fireburn.lochssh.ssh

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingCaptureTest {
    @Test fun capturesOnlyOutputAfterExplicitStartAndBeforeStop() {
        val capture = RecordingCapture()
        capture.append(byteArrayOf(0), 1)
        val output = ByteArrayOutputStream()
        val writer = RecordingWriter(output)
        capture.start(writer)
        capture.append(byteArrayOf(1, 2), 2)
        capture.stop()
        capture.append(byteArrayOf(3), 1)
        assertTrue(writer.awaitClosed(3_000))
        assertArrayEquals(byteArrayOf(1, 2), output.toByteArray())
        val replacement = RecordingCapture()
        replacement.append(byteArrayOf(4), 1)
        assertArrayEquals(byteArrayOf(1, 2), output.toByteArray())
    }

    @Test fun readerCapturesExactBytesAndClosesAtEndOfOutput() {
        val bytes = ByteArray(128 * 1024) { (it % 256).toByte() }
        val output = ByteArrayOutputStream()
        val writer = RecordingWriter(output)
        val capture = RecordingCapture()
        capture.start(writer)
        val rendered = ByteArrayOutputStream()
        ShellOutputReader.read(ByteArrayInputStream(bytes), { true }, { buffer, count ->
            capture.append(buffer, count)
            rendered.write(buffer, 0, count)
        }, { capture.stop() })
        assertTrue(writer.awaitClosed(3_000))
        assertNull(writer.state.value.error)
        assertArrayEquals(bytes, output.toByteArray())
        assertArrayEquals(bytes, rendered.toByteArray())
    }

    @Test fun recordingFailureDoesNotStopReaderOutput() {
        val writer = RecordingWriter(object : OutputStream() {
            override fun write(value: Int) { throw IOException("disk full") }
        })
        val capture = RecordingCapture()
        capture.start(writer)
        capture.append(byteArrayOf(0), 1)
        assertTrue(writer.awaitClosed(3_000))
        val rendered = ByteArrayOutputStream()
        ShellOutputReader.read(ByteArrayInputStream(byteArrayOf(1, 2, 3)), { true }, { buffer, count ->
            capture.append(buffer, count)
            rendered.write(buffer, 0, count)
        }, { capture.stop() })
        assertEquals("Could not write recording", writer.state.value.error)
        assertArrayEquals(byteArrayOf(1, 2, 3), rendered.toByteArray())
    }

    @Test fun activeRecordingCannotBeReplacedButClosedOneCan() {
        val capture = RecordingCapture()
        val first = RecordingWriter(ByteArrayOutputStream())
        val secondOutput = ByteArrayOutputStream()
        val second = RecordingWriter(secondOutput)
        try {
            capture.start(first)
            assertThrows(IllegalStateException::class.java) { capture.start(second) }
            capture.stop()
            assertTrue(first.awaitClosed(3_000))
            capture.start(second)
            capture.append(byteArrayOf(7), 1)
        } finally { first.stop(); second.stop() }
        assertTrue(second.awaitClosed(3_000))
        assertArrayEquals(byteArrayOf(7), secondOutput.toByteArray())
    }
}
