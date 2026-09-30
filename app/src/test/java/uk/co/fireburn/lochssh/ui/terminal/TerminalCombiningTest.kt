package uk.co.fireburn.lochssh.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalCombiningTest {
    @Test
    fun accentsAndEnclosingMarksStayWithTheirBase() {
        val buffer = TerminalBuffer(8, 4)
        buffer.send("e\u0301\u0327a\u20DDx")
        assertEquals("e\u0301\u0327", buffer.cellAt(0, 0).text)
        assertEquals("a\u20DD", buffer.cellAt(0, 1).text)
        assertEquals("x", buffer.cellAt(0, 2).text)
        assertEquals(0 to 3, buffer.cursor)
        val point = TerminalBuffer.Point(0, 0)
        assertEquals("e\u0301\u0327", buffer.selectedText(point, point, 0))
    }

    @Test
    fun marksAtTheRightEdgeDoNotTriggerAnotherLine() {
        val buffer = TerminalBuffer(4, 3)
        buffer.send("abcd\u0301")
        assertEquals("d\u0301", buffer.cellAt(0, 3).text)
        assertEquals(0, buffer.cursorRow)
        buffer.send("x")
        assertEquals("x", buffer.cellAt(1, 0).text)
        buffer.send("\u001B[?7l\u001B[2;4Hy\u0308")
        assertEquals("y\u0308", buffer.cellAt(1, 3).text)
        buffer.send("\u001B[?1049hother\u001B[?1049l\u0327")
        assertEquals("y\u0308\u0327", buffer.cellAt(1, 3).text)
    }

    @Test
    fun splitSupplementaryMarksAndBaseCharactersRemainIntact() {
        val buffer = TerminalBuffer(8, 4)
        val text = "𐐷\u0301" + String(Character.toChars(0xE0100))
        for (byte in text.toByteArray(Charsets.UTF_8)) buffer.feed(byteArrayOf(byte), 1)
        assertEquals(text, buffer.cellAt(0, 0).text)
        assertEquals(0 to 1, buffer.cursor)
        assertEquals("$text\n", buffer.transcript())
    }

    @Test
    fun marksKeepTheBaseStyleThroughResizingAndScreenChanges() {
        val buffer = TerminalBuffer(8, 4)
        buffer.send("\u001B[31;1me\u001B[0m\u0301\u001B[?1049hx\u0308")
        buffer.resize(6, 3)
        assertEquals("x\u0308", buffer.cellAt(0, 0).text)
        buffer.send("\u001B[?1049l")
        assertEquals("e\u0301", buffer.cellAt(0, 0).text)
        assertEquals(TerminalBuffer.ANSI_COLORS[1], buffer.cellAt(0, 0).fg)
        assertTrue(buffer.cellAt(0, 0).bold)
    }

    @Test
    fun leadingMarksStayOnTheCurrentBlankCell() {
        val buffer = TerminalBuffer(8, 4)
        buffer.send("\u0301")
        assertEquals(" \u0301", buffer.cellAt(0, 0).text)
        assertEquals(0 to 0, buffer.cursor)
    }

    @Test
    fun disabledWrappingDoesNotAttachMarksToAnUnwrittenLastColumn() {
        val buffer = TerminalBuffer(4, 3)
        buffer.send("\u001B[?7labc\u0301")
        assertEquals("c\u0301", buffer.cellAt(0, 2).text)
        assertEquals(" ", buffer.cellAt(0, 3).text)
        buffer.send("d\u0308")
        assertEquals("d\u0308", buffer.cellAt(0, 3).text)
    }

    @Test(timeout = 2_000)
    fun excessiveMarksCannotGrowACellWithoutLimit() {
        val buffer = TerminalBuffer(8, 4)
        buffer.send("e" + "\u0301".repeat(10_000) + "x")
        assertTrue(buffer.cellAt(0, 0).text.length <= 128)
        assertEquals("x", buffer.cellAt(0, 1).text)
        assertEquals(0 to 2, buffer.cursor)
    }

    private fun TerminalBuffer.send(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        feed(bytes, bytes.size)
    }
}
