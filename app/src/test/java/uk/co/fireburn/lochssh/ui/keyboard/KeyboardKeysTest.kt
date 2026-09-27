package uk.co.fireburn.lochssh.ui.keyboard

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import uk.co.fireburn.lochssh.data.KeyboardAction
import uk.co.fireburn.lochssh.data.KeyboardButton
import uk.co.fireburn.lochssh.ui.terminal.TerminalKey
import uk.co.fireburn.lochssh.ui.terminal.TerminalKeyEncoder

class KeyboardKeysTest {
    @Test
    fun mapsEverySendingKey() {
        val controls = setOf(
            KeyboardAction.CTRL, KeyboardAction.ALT, KeyboardAction.FN,
            KeyboardAction.FONT_UP, KeyboardAction.FONT_DOWN, KeyboardAction.KEYBOARD
        )
        KeyboardAction.entries.filter { it != KeyboardAction.TEXT }.forEach { action ->
            val key = KeyboardButton(action).terminalKey()
            if (action in controls) assertNull(key) else {
                assertNotNull(key)
                assertNotNull(TerminalKeyEncoder.encode(requireNotNull(key), false, false, false))
            }
        }
        assertArrayEquals(byteArrayOf(13), encode(TerminalKey.Enter))
    }

    @Test
    fun encodesControlCharactersAndAltTogether() {
        assertArrayEquals(byteArrayOf(3), encode(TerminalKey.Text("c"), ctrl = true))
        assertArrayEquals(byteArrayOf(28), encode(TerminalKey.Text("\\"), ctrl = true))
        assertArrayEquals(byteArrayOf(0), encode(TerminalKey.Text(" "), ctrl = true))
        assertArrayEquals(byteArrayOf(127), encode(TerminalKey.Text("?"), ctrl = true))
        assertArrayEquals(byteArrayOf(27, 3), encode(TerminalKey.Text("c"), ctrl = true, alt = true))
    }

    @Test
    fun encodesCustomTextWithoutDroppingUnsupportedControlCombination() {
        assertArrayEquals("é".toByteArray(), encode(KeyboardButton(KeyboardAction.TEXT, "é").terminalKey()!!))
        assertArrayEquals("1".toByteArray(), encode(TerminalKey.Text("1"), ctrl = true))
    }

    private fun encode(key: TerminalKey, ctrl: Boolean = false, alt: Boolean = false) =
        TerminalKeyEncoder.encode(key, ctrl, alt, false)
}
