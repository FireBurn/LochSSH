package uk.co.fireburn.lochssh.ui.keyboard

import android.view.KeyEvent
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class ImeKeyEncoderAndroidTest {
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
