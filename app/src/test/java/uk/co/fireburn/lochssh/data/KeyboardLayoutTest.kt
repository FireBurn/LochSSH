package uk.co.fireburn.lochssh.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class KeyboardLayoutTest {
    @Test
    fun preservesDefaultLayout() {
        assertEquals(KeyboardLayout.DEFAULT, KeyboardLayout.decode(KeyboardLayout.DEFAULT.encode()))
    }

    @Test
    fun preservesCustomKeysAndOrder() {
        val layout = KeyboardLayout(listOf(
            listOf(KeyboardButton(KeyboardAction.TEXT, ",:é"), KeyboardButton(KeyboardAction.CTRL)),
            listOf(KeyboardButton(KeyboardAction.ENTER))
        ))
        assertEquals(layout, KeyboardLayout.decode(layout.encode()))
    }

    @Test
    fun restoresDefaultForInvalidSavedLayout() {
        listOf(null, "", "UNKNOWN:", "TEXT:AA==", "ESC:,", "x".repeat(4097)).forEach {
            assertEquals(KeyboardLayout.DEFAULT, KeyboardLayout.decode(it))
        }
    }

    @Test
    fun rejectsEmptyAndOversizedLayouts() {
        assertThrows(IllegalArgumentException::class.java) { KeyboardLayout(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { KeyboardLayout(listOf(emptyList())) }
        assertThrows(IllegalArgumentException::class.java) {
            KeyboardLayout(List(5) { listOf(KeyboardButton(KeyboardAction.ESC)) })
        }
        assertThrows(IllegalArgumentException::class.java) {
            KeyboardLayout(listOf(List(11) { KeyboardButton(KeyboardAction.ESC) }))
        }
    }

    @Test
    fun rejectsEmptyLongAndControlText() {
        listOf("", "a\nb", "a\rb", "\u001b", "x".repeat(17)).forEach { text ->
            assertThrows(IllegalArgumentException::class.java) { KeyboardButton(KeyboardAction.TEXT, text) }
        }
    }
}
