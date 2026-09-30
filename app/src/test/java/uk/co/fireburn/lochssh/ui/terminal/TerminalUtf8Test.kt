package uk.co.fireburn.lochssh.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalUtf8Test {
    @Test
    fun decodesEveryUnicodeScalarValue() {
        var actual = -1
        val decoder = TerminalUtf8 { actual = it }
        for (code in 0..0x10FFFF) {
            if (code in 0xD800..0xDFFF) continue
            val bytes = String(Character.toChars(code)).toByteArray(Charsets.UTF_8)
            for (byte in bytes) decoder.feed(byte.toInt() and 0xFF)
            assertEquals(code, actual)
        }
    }

    @Test
    fun replacesOverlongSurrogateAndOutOfRangeSequences() {
        val cases = listOf(
            listOf(0xC0, 0xAF) to 2,
            listOf(0xE0, 0x80, 0xAF) to 3,
            listOf(0xED, 0xA0, 0x80) to 3,
            listOf(0xF0, 0x80, 0x80, 0xAF) to 4,
            listOf(0xF4, 0x90, 0x80, 0x80) to 4,
            listOf(0xF5, 0x80, 0x80, 0x80) to 4
        )
        for ((bytes, replacements) in cases) {
            val output = mutableListOf<Int>()
            val decoder = TerminalUtf8(output::add)
            bytes.forEach(decoder::feed)
            decoder.feed('x'.code)
            assertEquals(List(replacements) { 0xFFFD } + 'x'.code, output)
        }
    }

    @Test
    fun interruptedSequenceDoesNotConsumeTheNextValidCharacter() {
        val output = mutableListOf<Int>()
        val decoder = TerminalUtf8(output::add)
        listOf(0xE2, 0x82, 0xC3, 0xA9, 0xE2, 'x'.code).forEach(decoder::feed)
        assertEquals(listOf(0xFFFD, 'é'.code, 0xFFFD, 'x'.code), output)
    }

    @Test
    fun incompleteSequenceIsReplacedOnceAtTheEnd() {
        val output = mutableListOf<Int>()
        val decoder = TerminalUtf8(output::add)
        listOf(0xF0, 0x9F, 0x92).forEach(decoder::feed)
        decoder.finish()
        decoder.finish()
        assertEquals(listOf(0xFFFD), output)
    }

    @Test
    fun terminalHandlesBrokenInputBeforeLineFeedsAndOtherCharacters() {
        val buffer = TerminalBuffer(12, 4)
        val bytes = byteArrayOf(0xE2.toByte(), 0x0A, 0xC3.toByte(), 0xA9.toByte(), 0xE2.toByte(), 'x'.code.toByte())
        buffer.feed(bytes, bytes.size)
        assertEquals('�', buffer.cellAt(0, 0).ch)
        assertEquals(" é�x", buffer.rowText(1, 0, 4))
        buffer.feed(byteArrayOf(0x7F, 'y'.code.toByte()), 2)
        assertEquals('y', buffer.cellAt(1, 4).ch)
    }
}
