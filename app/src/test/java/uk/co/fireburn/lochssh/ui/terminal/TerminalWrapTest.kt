package uk.co.fireburn.lochssh.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalWrapTest {
    @Test
    fun fullLineWrapsOnlyWhenAnotherCharacterArrives() {
        val buffer = TerminalBuffer(4, 3)
        buffer.send("abcd")
        assertEquals("abcd", buffer.rowText(0, 0, 4))
        assertEquals(0, buffer.cursorRow)
        buffer.send("e")
        assertEquals('e', buffer.cellAt(1, 0).ch)
    }

    @Test
    fun lineFeedCancelsThePendingWrap() {
        val buffer = TerminalBuffer(4, 3)
        buffer.send("abcd\ne")
        assertEquals('e', buffer.cellAt(1, 3).ch)
        assertEquals(1, buffer.cursorRow)
        assertEquals("    ", buffer.rowText(2, 0, 4))
    }

    @Test
    fun disabledWrappingKeepsReplacingTheLastColumn() {
        val buffer = TerminalBuffer(4, 3)
        buffer.send("\u001B[?7labcdef")
        assertEquals("abcf", buffer.rowText(0, 0, 4))
        assertEquals(0 to 3, buffer.cursor)
        buffer.send("\u001B[?7hgh")
        assertEquals("abcg", buffer.rowText(0, 0, 4))
        assertEquals('h', buffer.cellAt(1, 0).ch)
    }

    @Test
    fun turningWrappingOffCancelsAnExistingPendingWrap() {
        val buffer = TerminalBuffer(4, 3)
        buffer.send("abcd\u001B[?7le")
        assertEquals("abce", buffer.rowText(0, 0, 4))
        assertEquals(0, buffer.cursorRow)
    }

    @Test
    fun backspaceFromTheLastColumnMovesOneColumnLeft() {
        val buffer = TerminalBuffer(4, 3)
        buffer.send("abcd\bx")
        assertEquals("abxd", buffer.rowText(0, 0, 4))
    }

    @Test
    fun cursorMovementCancelsThePendingWrap() {
        val buffer = TerminalBuffer(4, 3)
        buffer.send("abcd\u001B[Bx")
        assertEquals('x', buffer.cellAt(1, 3).ch)
        buffer.send("\u001B[Dy")
        assertEquals('y', buffer.cellAt(1, 2).ch)
        buffer.send("\u001B[1dz")
        assertEquals('z', buffer.cellAt(0, 3).ch)
    }

    private fun TerminalBuffer.send(text: String) {
        val bytes = text.toByteArray(Charsets.US_ASCII)
        feed(bytes, bytes.size)
    }
}
