package uk.co.fireburn.lochssh.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalLineWrappingTest {
    private fun TerminalBuffer.feed(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        feed(bytes, bytes.size)
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
