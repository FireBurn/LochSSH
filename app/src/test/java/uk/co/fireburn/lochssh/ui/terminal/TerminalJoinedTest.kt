package uk.co.fireburn.lochssh.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalJoinedTest {
    private val family = "👨‍👩‍👧"

    @Test
    fun joinedEmojiShareOneTwoColumnCell() {
        val buffer = TerminalBuffer(10, 3)
        buffer.send("a${family}b")
        assertEquals("a", buffer.cellAt(0, 0).text)
        assertEquals(family, buffer.cellAt(0, 1).text)
        assertEquals(2, buffer.cellAt(0, 1).columns)
        assertEquals("b", buffer.cellAt(0, 3).text)
        assertEquals(0 to 4, buffer.cursor)
    }

    @Test
    fun skinToneJoinsTheEmojiBeforeIt() {
        val buffer = TerminalBuffer(10, 3)
        buffer.send("👍🏽x")
        assertEquals("👍🏽", buffer.cellAt(0, 0).text)
        assertEquals("x", buffer.cellAt(0, 2).text)
    }

    @Test
    fun joinerAcrossSplitReadsAndPlainTextAfterwards() {
        val buffer = TerminalBuffer(10, 3)
        for (byte in family.toByteArray(Charsets.UTF_8)) buffer.feed(byteArrayOf(byte), 1)
        buffer.send("zz")
        assertEquals(family, buffer.cellAt(0, 0).text)
        assertEquals("z", buffer.cellAt(0, 2).text)
        assertEquals("z", buffer.cellAt(0, 3).text)
    }

    @Test
    fun joinerDoesNotAffectLaterCharacters() {
        val buffer = TerminalBuffer(10, 3)
        buffer.send("a‍b")
        buffer.send("cd")
        assertEquals("a‍b", buffer.cellAt(0, 0).text)
        assertEquals("c", buffer.cellAt(0, 1).text)
    }

    @Test
    fun regionalIndicatorPairsFormOneFlag() {
        val buffer = TerminalBuffer(10, 3)
        val flag = "\uD83C\uDDEC\uD83C\uDDE7"
        buffer.send("a${flag}b\uD83C\uDDEC")
        assertEquals(flag, buffer.cellAt(0, 1).text)
        assertEquals(2, buffer.cellAt(0, 1).columns)
        assertEquals("b", buffer.cellAt(0, 3).text)
        assertEquals(1, buffer.cellAt(0, 4).columns)
        assertEquals(0 to 5, buffer.cursor)
    }

    @Test
    fun emojiSelectorWidensASymbolToTwoColumns() {
        val buffer = TerminalBuffer(10, 3)
        buffer.send("a\u2764\uFE0Fb\u2764c")
        assertEquals("\u2764\uFE0F", buffer.cellAt(0, 1).text)
        assertEquals(2, buffer.cellAt(0, 1).columns)
        assertEquals("b", buffer.cellAt(0, 3).text)
        assertEquals(1, buffer.cellAt(0, 4).columns)
        assertEquals("c", buffer.cellAt(0, 5).text)
    }

    @Test
    fun emojiSelectorKeepsAsciiAndEdgeCellsNarrow() {
        val buffer = TerminalBuffer(4, 3)
        buffer.send("1\uFE0Fab\u2764\uFE0F")
        assertEquals("1\uFE0F", buffer.cellAt(0, 0).text)
        assertEquals(1, buffer.cellAt(0, 0).columns)
        assertEquals("\u2764\uFE0F", buffer.cellAt(0, 3).text)
        assertEquals(1, buffer.cellAt(0, 3).columns)
        assertEquals(0, buffer.cursorRow)
    }

    private fun TerminalBuffer.send(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        feed(bytes, bytes.size)
    }
}
