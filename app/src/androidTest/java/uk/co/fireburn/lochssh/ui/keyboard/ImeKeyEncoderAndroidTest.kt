package uk.co.fireburn.lochssh.ui.keyboard

import android.view.KeyEvent
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class ImeKeyEncoderAndroidTest {
    @Test
    fun encodesFunctionAndNavigationKeys() {
        val codes = listOf("\u001bOP", "\u001bOQ", "\u001bOR", "\u001bOS",
            "\u001b[15~", "\u001b[17~", "\u001b[18~", "\u001b[19~",
            "\u001b[20~", "\u001b[21~", "\u001b[23~", "\u001b[24~")
        codes.forEachIndexed { index, expected ->
            assertArrayEquals(expected.toByteArray(), ImeKeyEncoder.encode(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F1 + index)))
        }
        fun check(code: Int, modifiers: Int, expected: String) {
            assertArrayEquals(expected.toByteArray(), ImeKeyEncoder.encode(
                KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, 0, modifiers)))
        }
        check(KeyEvent.KEYCODE_F1, KeyEvent.META_CTRL_ON, "\u001b[1;5P")
        check(KeyEvent.KEYCODE_F12, KeyEvent.META_SHIFT_ON, "\u001b[24;2~")
        check(KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.META_CTRL_ON, "\u001b[1;5H")
        check(KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.META_ALT_ON, "\u001b[6;3~")
        check(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.META_SHIFT_ON, "\u001b[1;2A")
        check(KeyEvent.KEYCODE_TAB, KeyEvent.META_SHIFT_ON, "\u001b[Z")
        check(KeyEvent.KEYCODE_INSERT, 0, "\u001b[2~")
    }

    @Test
    fun preservesAltWithControlLetters() {
        assertArrayEquals(byteArrayOf(27, 3), ImeKeyEncoder.encodeText("c", ctrl = true, alt = true))
        val event = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_C, 0,
            KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON)
        assertArrayEquals(byteArrayOf(27, 3), ImeKeyEncoder.encode(event))
    }

    @Test
    fun encodesControlPunctuation() {
        assertArrayEquals(byteArrayOf(28), ImeKeyEncoder.encodeText("\\", ctrl = true))
        assertArrayEquals(byteArrayOf(31), ImeKeyEncoder.encodeText("_", ctrl = true))
        assertArrayEquals(byteArrayOf(127), ImeKeyEncoder.encodeText("?", ctrl = true))
        val event = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACKSLASH, 0,
            KeyEvent.META_CTRL_ON)
        assertArrayEquals(byteArrayOf(28), ImeKeyEncoder.encode(event))
    }
}
