package uk.co.fireburn.lochssh.ui.terminal

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TerminalModifiersAndroidTest {
    @get:Rule val ui = createComposeRule()

    @Test fun changingSessionClearsLatchedModifiers() = checkTargetChange(true)

    @Test fun reconnectingClearsLatchedModifiers() = checkTargetChange(false)

    private fun checkTargetChange(changeSession: Boolean) {
        val firstManager = Any()
        val target = mutableStateOf(1L to firstManager)
        val redraw = mutableStateOf(0)
        lateinit var modifiers: TerminalModifiers
        ui.setContent {
            redraw.value
            modifiers = rememberTerminalModifiers(target.value)
        }
        val first = modifiers
        ui.runOnIdle {
            modifiers.ctrl = true
            modifiers.alt = true
            modifiers.fn = true
            redraw.value++
        }
        ui.runOnIdle {
            assertSame(first, modifiers)
            assertTrue(modifiers.ctrl && modifiers.alt && modifiers.fn)
            target.value = if (changeSession) 2L to firstManager else 1L to Any()
        }
        ui.runOnIdle { assertFalse(modifiers.any) }
    }
}
