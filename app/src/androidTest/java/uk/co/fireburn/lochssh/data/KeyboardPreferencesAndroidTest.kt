package uk.co.fireburn.lochssh.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.fireburn.lochssh.ui.terminal.TerminalTheme

class KeyboardPreferencesAndroidTest {
    @Test
    fun savesReloadsAndResetsLayoutWithoutUsingAppSettings() {
        val testContext = InstrumentationRegistry.getInstrumentation().targetContext
        val fileName = "keyboard_test_${UUID.randomUUID()}"
        val context = object : ContextWrapper(testContext) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                testContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)
        }
        try {
            val preferences = AppPreferences(context)
            val layout = KeyboardLayout(listOf(listOf(
                KeyboardButton(KeyboardAction.CTRL), KeyboardButton(KeyboardAction.TEXT, "c")
            )))
            preferences.setKeyboardLayout(layout)
            preferences.setTerminalTheme(TerminalTheme.PAPER)
            assertTrue(testContext.getSharedPreferences(fileName, Context.MODE_PRIVATE).edit().commit())
            assertEquals(layout, preferences.keyboardLayout.value)
            assertEquals(layout, AppPreferences(context).keyboardLayout.value)
            assertEquals(TerminalTheme.PAPER, AppPreferences(context).terminalTheme.value)

            preferences.reset()

            assertEquals(KeyboardLayout.DEFAULT, preferences.keyboardLayout.value)
            assertEquals(KeyboardLayout.DEFAULT, AppPreferences(context).keyboardLayout.value)
            assertEquals(TerminalTheme.LOCH, preferences.terminalTheme.value)
            assertEquals(TerminalTheme.LOCH, AppPreferences(context).terminalTheme.value)
        } finally {
            testContext.deleteSharedPreferences(fileName)
        }
    }
}
