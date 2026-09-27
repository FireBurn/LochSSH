package uk.co.fireburn.lochssh.ssh

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ShellOutputReaderTest {
    @Test
    fun reportsOutputAndStopsOnceAtEof() {
        val output = mutableListOf<String>()
        var stops = 0
        ShellOutputReader.read(ByteArrayInputStream("hello".toByteArray()), { true }, { bytes, count ->
            output += bytes.copyOf(count).toString(Charsets.UTF_8)
        }, { failure ->
            assertNull(failure)
            stops++
        })

        assertEquals(listOf("hello"), output)
        assertEquals(1, stops)
    }

    @Test
    fun reportsReadFailureAndStopsOnce() {
        val readFailure = IOException("Stream closed")
        val input = object : InputStream() {
            override fun read(): Int = throw readFailure
        }
        var stops = 0
        ShellOutputReader.read(input, { true }, { _, _ -> error("Unexpected output") }, { failure ->
            assertSame(readFailure, failure)
            stops++
        })

        assertEquals(1, stops)
    }

    @Test
    fun stopsWithoutReadingAfterCancellation() {
        val input = object : InputStream() {
            override fun read(): Int = error("Read after cancellation")
        }
        var stops = 0
        ShellOutputReader.read(input, { false }, { _, _ -> error("Unexpected output") }, { failure ->
            assertNull(failure)
            stops++
        })

        assertEquals(1, stops)
    }
}
