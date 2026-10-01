package uk.co.fireburn.lochssh.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import uk.co.fireburn.lochssh.ui.terminal.TerminalTheme
import uk.co.fireburn.lochssh.ui.terminal.TerminalTypeface

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
            assertFalse(preferences.sessionShortcuts.value)
            assertTrue(preferences.wrapLines.value)
            val layout = KeyboardLayout(listOf(listOf(
                KeyboardButton(KeyboardAction.CTRL), KeyboardButton(KeyboardAction.TEXT, "c")
            )))
            preferences.setKeyboardLayout(layout)
            preferences.setTerminalTheme(TerminalTheme.PAPER)
            preferences.setTerminalTypeface(TerminalTypeface.JETBRAINS)
            preferences.setSessionShortcuts(true)
            preferences.setWrapLines(false)
            assertTrue(testContext.getSharedPreferences(fileName, Context.MODE_PRIVATE).edit().commit())
            assertEquals(layout, preferences.keyboardLayout.value)
            assertEquals(layout, AppPreferences(context).keyboardLayout.value)
            assertEquals(TerminalTheme.PAPER, AppPreferences(context).terminalTheme.value)
            assertEquals(TerminalTypeface.JETBRAINS, preferences.terminalTypeface.value)
            assertEquals(TerminalTypeface.JETBRAINS, AppPreferences(context).terminalTypeface.value)
            assertTrue(AppPreferences(context).sessionShortcuts.value)
            assertFalse(preferences.wrapLines.value)
            assertFalse(AppPreferences(context).wrapLines.value)

            preferences.reset()

            assertEquals(KeyboardLayout.DEFAULT, preferences.keyboardLayout.value)
            assertEquals(KeyboardLayout.DEFAULT, AppPreferences(context).keyboardLayout.value)
            assertEquals(TerminalTheme.LOCH, preferences.terminalTheme.value)
            assertEquals(TerminalTheme.LOCH, AppPreferences(context).terminalTheme.value)
            assertEquals(TerminalTypeface.NOTO, preferences.terminalTypeface.value)
            assertEquals(TerminalTypeface.NOTO, AppPreferences(context).terminalTypeface.value)
            assertFalse(preferences.sessionShortcuts.value)
            assertFalse(AppPreferences(context).sessionShortcuts.value)
            assertTrue(preferences.wrapLines.value)
            assertTrue(AppPreferences(context).wrapLines.value)
        } finally {
            testContext.deleteSharedPreferences(fileName)
        }
    }
}
