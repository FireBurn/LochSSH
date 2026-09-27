package uk.co.fireburn.lochssh.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalTranscriptTest {
    private fun TerminalBuffer.send(text: String) {
        val bytes = text.toByteArray()
        feed(bytes, bytes.size)
    }

    @Test
    fun includesScrollbackWithoutColorCodesOrBlankTail() {
        val buffer = TerminalBuffer(20, 2)
        buffer.send("one\r\n\u001b[31mtwo\r\nthree")
        assertEquals("one\ntwo\nthree\n", buffer.transcript())
    }

    @Test
    fun exportsAlternateScreenWithoutUnrelatedShellHistory() {
        val buffer = TerminalBuffer(20, 2)
        buffer.send("shell\r\nold\r\ntext\u001b[?1049happ")
        assertEquals("app\n", buffer.transcript())
        buffer.send("\u001b[?1049l")
        assertEquals("shell\nold\ntext\n", buffer.transcript())
    }
}
