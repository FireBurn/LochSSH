package uk.co.fireburn.lochssh.ui.terminal

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalMouseTest {
    @Test fun utf8CoordinatesUseMultipleBytesWithoutChangingShortCoordinates() {
        assertArrayEquals(TerminalMouse.wheel(true, 4, 7, false),
            TerminalMouse.wheel(true, 4, 7, false, true))
        assertEquals("\u001B[M`\u014D\u0121",
            TerminalMouse.wheel(true, 300, 256, false, true).toString(Charsets.UTF_8))
        assertEquals("\u001B[Ma\u07FF!",
            TerminalMouse.wheel(false, Int.MAX_VALUE, -1, false, true).toString(Charsets.UTF_8))
    }

    @Test fun sgrCoordinatesDoNotOverflowAndTakePriorityOverUtf8() {
        assertEquals("\u001B[<64;2147483648;1M",
            TerminalMouse.wheel(true, Int.MAX_VALUE, -1, true, true).toString(Charsets.US_ASCII))
    }

    @Test fun mouseEncodingsSwitchIndependentlyOfTrackingAndReset() {
        val terminal = TerminalBuffer(80, 24)
        fun feed(text: String) { val bytes = text.toByteArray(); terminal.feed(bytes, bytes.size) }
        feed("\u001B[?1000;1005h")
        assertTrue(terminal.mouseTracking)
        assertTrue(terminal.mouseUtf8)
        assertFalse(terminal.mouseSgr)
        feed("\u001B[?1006h\u001B[?1005l")
        assertTrue(terminal.mouseSgr)
        assertFalse(terminal.mouseUtf8)
        feed("\u001B[?1005h\u001B[?1006l")
        assertTrue(terminal.mouseUtf8)
        assertFalse(terminal.mouseSgr)
        feed("\u001B[?1005l")
        assertFalse(terminal.mouseUtf8)
        assertTrue(terminal.mouseTracking)
        feed("\u001B[?1005h\u001Bc")
        assertFalse(terminal.mouseTracking)
        assertFalse(terminal.mouseUtf8)
        assertFalse(terminal.mouseSgr)
    }

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
