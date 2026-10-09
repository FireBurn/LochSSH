package uk.co.fireburn.lochssh.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LineDisciplineTest {
    private fun LineDiscipline.type(text: String) = feed(text.toByteArray())

    @Test
    fun typedTextIsEchoedAndSentOnlyOnEnter() {
        val d = LineDiscipline()
        val typed = d.type("ls")
        assertEquals("ls", String(typed.echo))
        assertEquals(0, typed.toShell.size)
        val entered = d.type("\r")
        assertEquals("\r\n", String(entered.echo))
        assertEquals("ls\n", String(entered.toShell))
    }

    @Test
    fun backspaceRemovesTheLastCharacterIncludingMultiByteOnes() {
        val d = LineDiscipline()
        d.type("aé")
        val erased = d.feed(byteArrayOf(0x7F))
        assertEquals("\b \b", String(erased.echo))
        assertEquals("a\n", String(d.type("\r").toShell))
    }

    @Test
    fun backspaceOnAnEmptyLineDoesNothing() {
        val result = LineDiscipline().feed(byteArrayOf(0x08))
        assertEquals(0, result.echo.size)
    }

    @Test
    fun characterSplitAcrossReadsIsKeptWhole() {
        val d = LineDiscipline()
        val bytes = "é".toByteArray()
        assertEquals(0, d.feed(byteArrayOf(bytes[0])).echo.size)
        assertEquals("é", String(d.feed(byteArrayOf(bytes[1])).echo))
        assertEquals("é\n", String(d.type("\r").toShell))
    }

    @Test
    fun interruptDropsTheLineAndSignals() {
        val d = LineDiscipline()
        d.type("sleep 9")
        val result = d.feed(byteArrayOf(0x03))
        assertTrue(result.interrupt)
        assertEquals("^C\r\n", String(result.echo))
        assertEquals("\n", String(d.type("\r").toShell))
    }

    @Test
    fun endOfInputOnlyWorksOnAnEmptyLine() {
        val d = LineDiscipline()
        assertTrue(d.feed(byteArrayOf(0x04)).endOfInput)
        d.type("x")
        assertFalse(d.feed(byteArrayOf(0x04)).endOfInput)
    }

    @Test
    fun arrowKeysAndOtherEscapeSequencesAreDropped() {
        val d = LineDiscipline()
        val result = d.type("a\u001B[Ab\u001BOAc\u001B[1;5Dd")
        assertEquals("abcd", String(result.echo))
        assertEquals("abcd\n", String(d.type("\r").toShell))
    }

    @Test
    fun clearLineErasesEverythingTyped() {
        val d = LineDiscipline()
        d.type("abc")
        assertEquals("\b \b\b \b\b \b", String(d.feed(byteArrayOf(0x15)).echo))
    }
}
