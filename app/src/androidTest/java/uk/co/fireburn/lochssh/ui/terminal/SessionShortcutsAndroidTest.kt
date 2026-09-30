package uk.co.fireburn.lochssh.ui.terminal

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import uk.co.fireburn.lochssh.ui.keyboard.ImeKeyEncoder

class SessionShortcutsAndroidTest {
    private val modifiers = KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON

    @Test
    fun disabledShortcutsKeepRemoteNavigationEncoding() {
        val event = key(KeyEvent.KEYCODE_PAGE_UP)
        assertNull(SessionShortcuts.direction(event, false))
        assertArrayEquals("\u001B[5;7~".toByteArray(Charsets.US_ASCII), ImeKeyEncoder.encode(event))
    }

    @Test
    fun recognizesOnlyTheEnabledSessionChord() {
        assertEquals(-1, SessionShortcuts.direction(key(KeyEvent.KEYCODE_PAGE_UP), true))
        assertEquals(1, SessionShortcuts.direction(key(KeyEvent.KEYCODE_PAGE_DOWN), true))
        assertNull(SessionShortcuts.direction(key(KeyEvent.KEYCODE_PAGE_DOWN), false))
        assertNull(SessionShortcuts.direction(key(KeyEvent.KEYCODE_TAB), true))
        for (meta in listOf(0, KeyEvent.META_CTRL_ON, KeyEvent.META_ALT_ON,
            modifiers or KeyEvent.META_SHIFT_ON, modifiers or KeyEvent.META_META_ON)) {
            assertNull(SessionShortcuts.direction(key(KeyEvent.KEYCODE_PAGE_DOWN, meta), true))
        }
        assertNull(SessionShortcuts.direction(KeyEvent(0, 0, KeyEvent.ACTION_UP,
            KeyEvent.KEYCODE_PAGE_DOWN, 0, modifiers), true))
    }

    private fun key(code: Int, meta: Int = modifiers) = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, 0, meta)
}
