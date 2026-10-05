package uk.co.fireburn.lochssh.ssh

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingWriterTest {
    @Test fun copiesReusedBuffersAndDrainsAcceptedOutput() {
        val output = ByteArrayOutputStream()
        val writer = RecordingWriter(output)
        val bytes = byteArrayOf(0x1b, 0x5b, 0xf0.toByte(), 0x9f.toByte())
        assertTrue(writer.append(bytes, bytes.size))
        bytes.fill(0)
        assertTrue(writer.append(byteArrayOf(0x98.toByte(), 0x80.toByte()), 2))
        writer.stop()
        assertTrue(writer.awaitClosed(3_000))
        assertArrayEquals(byteArrayOf(0x1b, 0x5b, 0xf0.toByte(), 0x9f.toByte(), 0x98.toByte(), 0x80.toByte()), output.toByteArray())
        assertEquals(6L, writer.state.value.bytesWritten)
        assertNull(writer.state.value.error)
        assertFalse(writer.append(byteArrayOf(1), 1))
    }

    @Test fun sizeLimitStopsWithoutPartlyAcceptingTheNextChunk() {
        val output = ByteArrayOutputStream()
        val writer = RecordingWriter(output, sizeLimit = 3)
        assertTrue(writer.append(byteArrayOf(1, 2), 2))
        assertFalse(writer.append(byteArrayOf(3, 4), 2))
        assertTrue(writer.awaitClosed(3_000))
        assertArrayEquals(byteArrayOf(1, 2), output.toByteArray())
        assertEquals("Recording size limit reached", writer.state.value.error)
    }

    @Test fun writeFailureClosesTheStreamAndStopsRecording() {
        var closed = false
        val output = object : OutputStream() {
            override fun write(value: Int) { throw IOException("private file path") }
            override fun close() { closed = true }
        }
        val writer = RecordingWriter(output)
        writer.append(byteArrayOf(1), 1)
        assertTrue(writer.awaitClosed(3_000))
        assertTrue(closed)
        assertEquals("Could not write recording", writer.state.value.error)
        assertFalse(writer.append(byteArrayOf(2), 1))
    }

    @Test fun fullQueueStopsWithoutBlockingTheProducer() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val output = object : ByteArrayOutputStream() {
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                entered.countDown()
                check(release.await(3, TimeUnit.SECONDS))
                super.write(bytes, offset, length)
            }
        }
        val writer = RecordingWriter(output, queueCapacity = 1)
        try {
            assertTrue(writer.append(byteArrayOf(1), 1))
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            assertTrue(writer.append(byteArrayOf(2), 1))
            assertFalse(writer.append(byteArrayOf(3), 1))
        } finally { release.countDown(); writer.stop() }
        assertTrue(writer.awaitClosed(3_000))
        assertArrayEquals(byteArrayOf(1, 2), output.toByteArray())
        assertEquals("Recording stopped: storage could not keep up", writer.state.value.error)
    }

    @Test fun closeFailuresAreReported() {
        val writer = RecordingWriter(object : ByteArrayOutputStream() {
            override fun close() { throw IOException("close failed") }
        })
        writer.stop()
        assertTrue(writer.awaitClosed(3_000))
        assertEquals("Could not close recording", writer.state.value.error)
    }
}
