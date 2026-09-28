package uk.co.fireburn.lochssh.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalSessionTest {
    private fun TerminalSession.feed(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        feed(bytes, bytes.size)
    }

    @Test
    fun keepsShellAndAlternateScreenAfterMoreThanTheOldReplayLimit() {
        val terminal = TerminalSession(20, 5)
        terminal.feed("shell prompt\u001B[?1049h\u001B[31m")
        val updates = "\rprogress".repeat(8192)
        repeat(20) { terminal.feed(updates) }

        terminal.buffer.read {
            assertEquals("progress", terminal.buffer.rowText(0, 0, 20).trimEnd())
            assertEquals(TerminalBuffer.ANSI_COLORS[1], terminal.buffer.cellAt(0, 0).fg)
        }
        terminal.feed("\u001B[?1049l")
        assertEquals("shell prompt", terminal.buffer.rowText(0, 0, 20).trimEnd())
    }

    @Test
    fun keepsPendingUtf8AndEscapeSequencesWithoutAView() {
        val terminal = TerminalSession(20, 5)
        val first = byteArrayOf(0xc3.toByte())
        terminal.feed(first, first.size)
        val second = byteArrayOf(0xa9.toByte())
        terminal.feed(second, second.size)
        terminal.feed("\u001B[")
        terminal.feed("31mred")

        assertEquals("éred", terminal.buffer.rowText(0, 0, 20).trimEnd())
        assertEquals(TerminalBuffer.ANSI_COLORS[1], terminal.buffer.cellAt(0, 1).fg)
        assertEquals(4L, terminal.revision.value)
    }

    @Test
    fun keepsBoundedScrollbackAndUpdatesWhileNoViewIsCollecting() {
        val terminal = TerminalSession(20, 5)
        repeat(TerminalBuffer.MAX_SCROLLBACK_ROWS + 50) { terminal.feed("line $it\r\n") }

        assertEquals(TerminalBuffer.MAX_SCROLLBACK_ROWS, terminal.buffer.scrollbackSize)
        assertTrue(terminal.buffer.transcript().contains("line 2049"))
        assertEquals(2050L, terminal.revision.value)
    }
}
