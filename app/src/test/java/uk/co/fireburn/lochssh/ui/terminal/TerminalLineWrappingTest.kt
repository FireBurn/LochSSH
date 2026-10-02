package uk.co.fireburn.lochssh.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.Assert.assertTrue

class TerminalLineWrappingTest {
    @Test
    fun wrappingChangesWaitForTheBufferLock() {
        val buffer = TerminalBuffer(5, 3)
        val worker = Executors.newSingleThreadExecutor()
        try {
            val change = buffer.read {
                val pending = worker.submit { buffer.setLineWrapping(false) }
                var waited = false
                try {
                    pending.get(1, TimeUnit.SECONDS)
                } catch (_: TimeoutException) {
                    waited = true
                }
                assertTrue(waited)
                pending
            }
            change.get(5, TimeUnit.SECONDS)
            buffer.feed("123456789")
            assertEquals(0, buffer.cursorRow)
            assertEquals("12345", buffer.visibleRowText(0, 0, 5, 0))
        } finally {
            worker.shutdownNow()
        }
    }

    private fun TerminalBuffer.feed(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        feed(bytes, bytes.size)
    }

    @Test
    fun clippedCharactersDoNotAddTheirMarksToVisibleText() {
        val buffer = TerminalBuffer(5, 3)
        buffer.setLineWrapping(false)
        buffer.feed("12345x\u0301\u0308")
        assertEquals("12345", buffer.visibleRowText(0, 0, 5, 0))
        buffer.feed("\re\u0301")
        assertEquals("e\u0301" + "2345", buffer.visibleRowText(0, 0, 5, 0))
    }

    @Test
    fun marksOnTheLastVisibleCharacterAreKept() {
        val buffer = TerminalBuffer(5, 3)
        buffer.setLineWrapping(false)
        buffer.feed("1234e\u0301")
        assertEquals("1234e\u0301", buffer.visibleRowText(0, 0, 5, 0))
    }

    @Test
    fun clipsLongProgressLinesUntilCarriageReturn() {
        val buffer = TerminalBuffer(5, 3)
        buffer.setLineWrapping(false)
        buffer.feed("12% compiling a long file\r34% compiling another file")
        assertEquals("34% c", buffer.visibleRowText(0, 0, 5, 0))
        assertEquals(0, buffer.cursorRow)
    }

    @Test
    fun serverResetCannotOverrideThePreference() {
        val buffer = TerminalBuffer(5, 3)
        buffer.setLineWrapping(false)
        buffer.feed("\u001Bc\u001B[?7h123456789")
        assertEquals("12345", buffer.visibleRowText(0, 0, 5, 0))
        assertEquals(0, buffer.cursorRow)
    }
}
