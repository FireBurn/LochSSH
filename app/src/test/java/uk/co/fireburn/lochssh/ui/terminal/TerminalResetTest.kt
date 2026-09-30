package uk.co.fireburn.lochssh.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalResetTest {
    @Test
    fun fullResetClearsScreenHistoryAndModesWithoutChangingDimensions() {
        val buffer = TerminalBuffer(8, 4)
        buffer.send("one\r\ntwo\r\nthree\r\nfour\r\nfive")
        val scrolled = buffer.totalScrolled
        assertTrue(buffer.scrollbackSize > 0)
        buffer.send("\u001B[?1;6;2004h\u001B[?7;25l\u001B[31;44;1;7m\u001B7\u001Bc")
        assertEquals(8, buffer.cols)
        assertEquals(4, buffer.rows)
        assertEquals(0, buffer.scrollbackSize)
        assertEquals(scrolled, buffer.totalScrolled)
        assertEquals(0 to 0, buffer.cursor)
        assertFalse(buffer.applicationCursor)
        assertFalse(buffer.bracketedPaste)
        assertTrue(buffer.cursorVisible)
        assertTrue(buffer.transcript().isBlank())
        buffer.send("x")
        assertEquals(TerminalBuffer.Cell('x', TerminalBuffer.DEFAULT_FG,
            TerminalBuffer.DEFAULT_BG, false), buffer.cellAt(0, 0))
    }

    @Test
    fun fullResetCannotResurrectTheOldNormalOrAlternateScreen() {
        val buffer = TerminalBuffer(8, 4)
        buffer.send("shell\u001B[?1049heditor\u001Bc\u001B[?1049lnew")
        assertEquals("new", buffer.rowText(0, 0, 8).trimEnd())
        assertFalse(buffer.transcript().contains("shell"))
        assertFalse(buffer.transcript().contains("editor"))
        buffer.send("\u001B[?1049hother\u001B[?1049l")
        assertEquals("new", buffer.rowText(0, 0, 8).trimEnd())
    }

    @Test
    fun softResetPreservesTextHistoryCursorAndBracketedPaste() {
        val buffer = TerminalBuffer(8, 4)
        buffer.send("one\r\ntwo\r\nthree\r\nfour\r\nfive")
        buffer.send("\u001B[2;3r\u001B[?1;6;2004h\u001B[?7;25l\u001B[31;44;1;7m\u001B[2;3H")
        val text = buffer.transcript()
        val history = buffer.scrollbackSize
        val cursor = buffer.cursor
        buffer.send("\u001B[!p")
        assertEquals(text, buffer.transcript())
        assertEquals(history, buffer.scrollbackSize)
        assertEquals(cursor, buffer.cursor)
        assertFalse(buffer.applicationCursor)
        assertTrue(buffer.cursorVisible)
        assertTrue(buffer.bracketedPaste)
        buffer.send("\u001B[1;1Hx\u001B[4;8Hyz")
        assertEquals(TerminalBuffer.Cell('x', TerminalBuffer.DEFAULT_FG,
            TerminalBuffer.DEFAULT_BG, false), buffer.visibleCellAt(0, 0, 1))
        assertEquals('z', buffer.cellAt(3, 0).ch)
    }

    @Test
    fun softResetKeepsTheAlternateScreenOpen() {
        val buffer = TerminalBuffer(8, 4)
        buffer.send("shell\u001B[?1049heditor\u001B[!p")
        assertEquals("editor", buffer.rowText(0, 0, 8).trimEnd())
        buffer.send("\u001B[?1049l")
        assertEquals("shell", buffer.rowText(0, 0, 8).trimEnd())
    }

    @Test
    fun softResetReplacesTheSavedCursorWithDefaultState() {
        val buffer = TerminalBuffer(8, 4)
        buffer.send("\u001B[2;3r\u001B[?6h\u001B[?7l\u001B[31m\u001B[2;3H\u001B7\u001B[!p\u001B8x")
        assertEquals('x', buffer.cellAt(0, 0).ch)
        assertEquals(TerminalBuffer.DEFAULT_FG, buffer.cellAt(0, 0).fg)
        buffer.send("\u001B[1;8Hab")
        assertEquals('a', buffer.cellAt(0, 7).ch)
        assertEquals('b', buffer.cellAt(1, 0).ch)
    }

    @Test
    fun resetCancelsAnUnfinishedCommandAndSplitCharacter() {
        val buffer = TerminalBuffer(8, 4)
        buffer.send("old\u001B[" + "1;".repeat(300) + "\u001Bcnew")
        assertEquals("new", buffer.rowText(0, 0, 8).trimEnd())
        buffer.feed(byteArrayOf(0xE2.toByte()), 1)
        buffer.send("\u001B")
        buffer.send("c")
        buffer.send("ok")
        assertEquals("ok", buffer.rowText(0, 0, 8).trimEnd())
    }

    @Test
    fun cancelAndSubstituteAbortAnEscapeSequence() {
        val buffer = TerminalBuffer(8, 4)
        buffer.send("\u001B[31\u0018x\u001B]unfinished\u001Ay")
        assertEquals("xy", buffer.rowText(0, 0, 8).trimEnd())
        assertEquals(TerminalBuffer.DEFAULT_FG, buffer.cellAt(0, 0).fg)
        buffer.send("\u001B[?1!p")
        assertEquals("xy", buffer.rowText(0, 0, 8).trimEnd())
    }

    private fun TerminalBuffer.send(text: String) {
        val bytes = text.toByteArray(Charsets.US_ASCII)
        feed(bytes, bytes.size)
    }
}
