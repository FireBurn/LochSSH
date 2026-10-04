package uk.co.fireburn.lochssh.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class TerminalScrollTest {
    @Test fun sendsArrowsOnlyWhenAlternateScrollingAndScreenAreEnabled() {
        val buffer = TerminalBuffer(80, 24)
        assertNull(input(buffer))
        buffer.send("\u001B[?1007h")
        assertNull(input(buffer))
        buffer.send("\u001B[?1049h")
        assertEquals("\u001B[A", input(buffer))
        assertEquals("\u001B[B", input(buffer, up = false))
        buffer.send("\u001B[?1h")
        assertEquals("\u001BOA", input(buffer))
        buffer.send("\u001B[?1007l")
        assertNull(input(buffer))
        buffer.send("\u001B[?1007h\u001B[?1049l")
        assertNull(input(buffer))
    }

    @Test fun mouseReportingTakesPriorityAndHistoryNeverSendsInput() {
        val buffer = TerminalBuffer(80, 24)
        buffer.send("\u001B[?1049;1007;1000;1006h")
        assertEquals("\u001B[<64;5;8M", input(buffer))
        assertNull(input(buffer, scrollOffset = 1))
        buffer.send("\u001B[?1000l")
        assertEquals("\u001B[A", input(buffer))
        assertNull(input(buffer, scrollOffset = 1))
    }

    @Test fun resetClearsAlternateScrollingAndTheAlternateScreen() {
        val buffer = TerminalBuffer(80, 24)
        buffer.send("\u001B[?1049;1007h\u001Bc")
        assertFalse(buffer.alternateScroll)
        assertFalse(buffer.isAlternateScreen)
        assertNull(input(buffer))
    }

    private fun input(buffer: TerminalBuffer, up: Boolean = true, scrollOffset: Int = 0) =
        TerminalScroll.remoteInput(buffer, up, 4, 7, scrollOffset)?.toString(Charsets.US_ASCII)

    private fun TerminalBuffer.send(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        feed(bytes, bytes.size)
    }
}
