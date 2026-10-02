package uk.co.fireburn.lochssh.ui.terminal

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalMouseTest {
    @Test fun encodesWheelDirectionAndCoordinates() {
        assertEquals("\u001B[<64;5;8M", TerminalMouse.wheel(true, 4, 7, true).toString(Charsets.US_ASCII))
        assertEquals("\u001B[<65;1;1M", TerminalMouse.wheel(false, 0, 0, true).toString(Charsets.US_ASCII))
        assertArrayEquals(byteArrayOf(27, 91, 77, 96, 37, 40), TerminalMouse.wheel(true, 4, 7, false))
    }

    @Test fun tracksRequestedModesAndReset() {
        val terminal = TerminalBuffer(80, 24)
        fun feed(text: String) { val bytes = text.toByteArray(); terminal.feed(bytes, bytes.size) }
        feed("\u001B[?1002;1006h")
        assertTrue(terminal.mouseTracking)
        assertTrue(terminal.mouseSgr)
        feed("\u001B[?1002l")
        assertFalse(terminal.mouseTracking)
        feed("\u001B[?1003h\u001Bc")
        assertFalse(terminal.mouseTracking)
        assertFalse(terminal.mouseSgr)
    }
}
