package uk.co.fireburn.lochssh.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalUnicodeTest {
    @Test
    fun cellsRetainCompleteSupplementaryCharactersAcrossSplitReads() {
        val buffer = TerminalBuffer(12, 4)
        val text = "a𐐷𝄞𐁱b"
        for (byte in text.toByteArray(Charsets.UTF_8)) buffer.feed(byteArrayOf(byte), 1)
        assertEquals(text, buffer.rowText(0, 0, 5))
        assertEquals("𐐷", buffer.cellAt(0, 1).text)
        assertEquals("𝄞", buffer.cellAt(0, 2).text)
        assertEquals("𐁱", buffer.cellAt(0, 3).text)
    }

    @Test
    fun copyingAndExportKeepBothSurrogateUnitsTogether() {
        val buffer = TerminalBuffer(12, 4)
        buffer.send("a𐐷b")
        val point = TerminalBuffer.Point(0, 1)
        assertEquals("𐐷", buffer.selectedText(point, point, 0))
        assertEquals("a𐐷b\n", buffer.transcript())
        assertEquals("a𐐷b\n", buffer.transcript().toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8))
    }

    @Test
    fun deletingAndInsertingCellsNeverSplitACharacter() {
        val buffer = TerminalBuffer(12, 4)
        buffer.send("a𐐷b\u001B[1;2H\u001B[P")
        assertEquals("ab", buffer.rowText(0, 0, 12).trimEnd())
        buffer.send("\u001B[@𐐷")
        assertEquals("a𐐷b", buffer.rowText(0, 0, 12).trimEnd())
        buffer.send("\u001B[1;2H\u001B[X")
        assertEquals("a b", buffer.rowText(0, 0, 12).trimEnd())
    }

    @Test
    fun scrollbackResizeAndScreenChangesRetainCompleteCharacters() {
        val buffer = TerminalBuffer(12, 3)
        buffer.send("𐐷\r\none\r\ntwo\r\nthree")
        assertEquals("𐐷", buffer.visibleRowText(0, 0, 1, 1))
        buffer.send("\u001B[?1049h𝄞")
        buffer.resize(8, 4)
        assertEquals("𝄞", buffer.cellAt(0, 0).text)
        buffer.send("\u001B[?1049l")
        assertEquals("𐐷", buffer.visibleRowText(0, 0, 1, 1))
        assertEquals("𐐷\none\ntwo\nthree\n", buffer.transcript())
    }

    @Test
    fun graphicCharacterSetsDoNotMapTheLowBitsOfSupplementaryCharacters() {
        val buffer = TerminalBuffer(12, 4)
        buffer.send("\u001B(0\u001B[31m𐁱q")
        assertEquals("𐁱─", buffer.rowText(0, 0, 2))
        assertEquals(TerminalBuffer.ANSI_COLORS[1], buffer.cellAt(0, 0).fg)
    }

    private fun TerminalBuffer.send(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        feed(bytes, bytes.size)
    }
}
