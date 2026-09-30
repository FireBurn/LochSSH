package uk.co.fireburn.lochssh.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalOriginTest {
    @Test
    fun originModeAddressesRowsWithinTheMargins() {
        val buffer = TerminalBuffer(8, 6)
        buffer.send("\u001B[2;5r")
        assertEquals(0 to 0, buffer.cursor)
        buffer.send("\u001B[?6h")
        assertEquals(1 to 0, buffer.cursor)
        buffer.send("\u001B[2;3Hx\u001B[4;2fy")
        assertEquals('x', buffer.cellAt(2, 2).ch)
        assertEquals('y', buffer.cellAt(4, 1).ch)
        buffer.send("\u001B[2dz")
        assertEquals('z', buffer.cellAt(2, 2).ch)
        buffer.send("\u001B[?6l")
        assertEquals(0 to 0, buffer.cursor)
    }

    @Test
    fun cursorMovementCannotLeaveTheOriginRegion() {
        val buffer = TerminalBuffer(8, 6)
        buffer.send("\u001B[2;5r\u001B[?6h\u001B[2147483647;2147483647H")
        assertEquals(4 to 7, buffer.cursor)
        buffer.send("\u001B[2147483647B\u001B[2147483647E")
        assertEquals(4 to 0, buffer.cursor)
        buffer.send("\u001B[2147483647A\u001B[2147483647F")
        assertEquals(1 to 0, buffer.cursor)
        buffer.send("\u001B[2147483647d")
        assertEquals(4 to 0, buffer.cursor)
    }

    @Test
    fun cursorReportsUseTheActiveOrigin() {
        val replies = mutableListOf<String>()
        val buffer = TerminalBuffer(8, 6, replies::add)
        buffer.send("\u001B[2;5r\u001B[?6h\u001B[3;4H\u001B[6n\u001B[?6n")
        assertEquals(listOf("\u001B[3;4R", "\u001B[?3;4R"), replies)
        buffer.send("\u001B[?6l\u001B[3;4H\u001B[6n")
        assertEquals("\u001B[3;4R", replies.last())
        assertEquals(2 to 3, buffer.cursor)
    }

    @Test
    fun changingMarginsHomesTheCursorAndRejectsInvalidRegions() {
        val buffer = TerminalBuffer(8, 6)
        buffer.send("\u001B[?6h\u001B[3;5r")
        assertEquals(2 to 0, buffer.cursor)
        buffer.send("\u001B[2;3H\u001B[5;2r\u001B[3;3r\u001B[2;99r")
        assertEquals(3 to 2, buffer.cursor)
        buffer.send("\u001B[99d")
        assertEquals(4 to 2, buffer.cursor)
        buffer.send("\u001B[r")
        assertEquals(0 to 0, buffer.cursor)
    }

    @Test
    fun savedCursorRestoresOriginWrappingAndTextColours() {
        val buffer = TerminalBuffer(8, 6)
        buffer.send("\u001B[2;5r\u001B[?6h\u001B[?7l\u001B[31;44;1m\u001B[2;3H\u001B7")
        buffer.send("\u001B[?6l\u001B[?7h\u001B[0m\u001B8x")
        val cell = buffer.cellAt(2, 2)
        assertEquals(TerminalBuffer.ANSI_COLORS[1], cell.fg)
        assertEquals(TerminalBuffer.ANSI_COLORS[4], cell.bg)
        assertEquals(true, cell.bold)
        buffer.send("\u001B[1;8Habc")
        assertEquals('c', buffer.cellAt(1, 7).ch)
        assertEquals(1 to 7, buffer.cursor)
    }

    @Test
    fun originAndMarginsSurviveScreenChangesAndResize() {
        val buffer = TerminalBuffer(8, 6)
        buffer.send("\u001B[2;5r\u001B[?6h\u001B[2;3H\u001B[?1049h\u001B[?6l")
        buffer.resize(5, 4)
        buffer.send("\u001B[?1049l\u001B[1;1Hx")
        assertEquals('x', buffer.cellAt(1, 0).ch)
        buffer.send("\u001B[99d")
        assertEquals(3, buffer.cursorRow)
        buffer.resize(3, 2)
        buffer.send("\u001B[1;1Hy\u001B[99d")
        assertEquals(1, buffer.cursorRow)
    }

    private fun TerminalBuffer.send(text: String) {
        val bytes = text.toByteArray(Charsets.US_ASCII)
        feed(bytes, bytes.size)
    }
}
