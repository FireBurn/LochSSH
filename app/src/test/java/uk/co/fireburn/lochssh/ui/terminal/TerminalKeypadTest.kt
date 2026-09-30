package uk.co.fireburn.lochssh.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalKeypadTest {
    @Test
    fun applicationKeysHaveDistinctSequencesAndModifiers() {
        "0123456789*+,-./=\r".zip("pqrstuvwxyjklmnoXM").forEach { (symbol, final) ->
            assertEquals("\u001BO$final", encode(symbol, true, false))
            assertEquals("\u001BO5$final", encode(symbol, true, false, 5))
        }
    }

    @Test
    fun numLockOverridesApplicationModeAndKeepsNumericKeys() {
        "0123456789*+,-./=\r()".forEach { symbol ->
            assertEquals(symbol.toString(), encode(symbol, true, true))
            assertEquals(symbol.toString(), encode(symbol, false, true))
        }
        assertEquals("\u001B1", encode('1', true, true, 3))
    }

    @Test
    fun unlockedNumericKeysUseNavigationAndCursorMode() {
        assertEquals("\u001B[2~", encode('0', false, false))
        assertEquals("\u001B[A", encode('8', false, false))
        assertEquals("\u001BOA", TerminalKeypad.encode('8', false, false,
            applicationCursor = true).toString(Charsets.US_ASCII))
        assertEquals("\u001B[1;5H", encode('7', false, false, 5))
        assertEquals("\u001B[3~", encode('.', false, false))
        assertEquals("\r", encode('\r', false, false))
    }

    @Test
    fun escapeAndPrivateModesEnableKeypadAndBothResetsDisableIt() {
        val buffer = TerminalBuffer(8, 4)
        buffer.send("\u001B=")
        assertTrue(buffer.applicationKeypad)
        buffer.send("\u001B>")
        assertFalse(buffer.applicationKeypad)
        buffer.send("\u001B[?66h\u001B[!p")
        assertFalse(buffer.applicationKeypad)
        buffer.send("\u001B[?66h\u001Bc")
        assertFalse(buffer.applicationKeypad)
        buffer.send("\u001B[?66h\u001B[?66l")
        assertFalse(buffer.applicationKeypad)
    }

    private fun encode(symbol: Char, application: Boolean, numLock: Boolean, modifier: Int = 1) =
        TerminalKeypad.encode(symbol, application, numLock, modifier).toString(Charsets.US_ASCII)

    private fun TerminalBuffer.send(text: String) {
        val bytes = text.toByteArray(Charsets.US_ASCII)
        feed(bytes, bytes.size)
    }
}
