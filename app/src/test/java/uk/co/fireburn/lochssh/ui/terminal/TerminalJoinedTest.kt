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

    private fun TerminalBuffer.send(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        feed(bytes, bytes.size)
    }
}
