package uk.co.fireburn.lochssh.ui.keyboard

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uk.co.fireburn.lochssh.ui.terminal.TerminalModifiers

class RepeatingKeyAndroidTest {
    @get:Rule val ui = createComposeRule()

    @Test
    fun heldArrowRepeatsAndStopsOnRelease() {
        var sent = 0
        ui.setContent {
            MaterialTheme {
                JuiceSshKeyboardBar(TerminalModifiers(), { sent++ }, {}, {})
            }
        }
        ui.mainClock.autoAdvance = false
        ui.onNodeWithText("▲").performTouchInput { down(center) }
        ui.mainClock.advanceTimeBy(700)
        ui.onNodeWithText("▲").performTouchInput { up() }
        ui.runOnIdle { assertTrue(sent >= 4) }
        val released = sent
        ui.mainClock.advanceTimeBy(500)
        ui.runOnIdle { assertEquals(released, sent) }
    }

    @Test
    fun shortPageDownPressSendsOnce() {
        var sent = 0
        ui.setContent {
            MaterialTheme {
                JuiceSshKeyboardBar(TerminalModifiers(), { sent++ }, {}, {})
            }
        }
        ui.onNodeWithText("PGDN").performTouchInput { click() }
        ui.runOnIdle { assertEquals(1, sent) }
    }
}
