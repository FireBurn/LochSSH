package uk.co.fireburn.lochssh.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalWidthTest {
    @Test
    fun matchesTheUnicode17SourceRangesForEveryCodePoint() {
        val expected = ByteArray(0x110000) { 1 }
        val input = requireNotNull(javaClass.classLoader?.getResourceAsStream("unicode17-terminal-widths.txt"))
        input.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val (range, width) = line.split(';')
                val (first, last) = range.split("..").map { it.toInt(16) }
                expected.fill(width.toByte(), first, last + 1)
            }
        }
        for (code in expected.indices) assertEquals(code.toString(16),
            expected[code].toInt(), TerminalWidth.columns(code))
    }

    @Test
    fun wideAndFullwidthCharactersUseTwoColumnsAndAmbiguousOnesUseOne() {
        assertEquals(2, TerminalWidth.columns('界'.code))
        assertEquals(2, TerminalWidth.columns('Ａ'.code))
        assertEquals(2, TerminalWidth.columns(0x1F600))
        assertEquals(2, TerminalWidth.columns(0x20000))
        assertEquals(1, TerminalWidth.columns('é'.code))
        assertEquals(1, TerminalWidth.columns('─'.code))
        assertEquals(1, TerminalWidth.columns(0x10437))
    }

    @Test
    fun marksHaveZeroWidthEvenWhenTheyAlsoHaveWideProperties() {
        assertEquals(0, TerminalWidth.columns(0x0301))
        assertEquals(0, TerminalWidth.columns(0x3099))
        assertEquals(0, TerminalWidth.columns(0xE0100))
        val buffer = TerminalBuffer(8, 4)
        val text = "e" + String(Character.toChars(0x1E6E3))
        val bytes = text.toByteArray(Charsets.UTF_8)
        buffer.feed(bytes, bytes.size)
        assertEquals(0 to 1, buffer.cursor)
        assertEquals(text, buffer.cellAt(0, 0).text)
    }
}
