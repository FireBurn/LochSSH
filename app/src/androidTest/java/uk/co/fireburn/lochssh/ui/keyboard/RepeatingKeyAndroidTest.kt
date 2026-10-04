package uk.co.fireburn.lochssh.ui.keyboard

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
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
    fun switchingConnectionsStopsAHeldKey() {
        val target = mutableStateOf(0)
        val sent = intArrayOf(0, 0)
        val modifiers = TerminalModifiers()
        ui.setContent {
            val current = target.value
            MaterialTheme {
                JuiceSshKeyboardBar(modifiers, { sent[current]++ }, {}, {}, inputTarget = current)
            }
        }
        ui.mainClock.autoAdvance = false
        ui.onNodeWithText("▲").performTouchInput { down(center) }
        ui.mainClock.advanceTimeBy(700)
        ui.runOnIdle {
            assertTrue(sent[0] >= 4)
            target.value = 1
        }
        ui.mainClock.advanceTimeBy(32)
        val previous = sent[0]
        ui.mainClock.advanceTimeBy(600)
        ui.runOnIdle {
            assertEquals(previous, sent[0])
            assertEquals(0, sent[1])
        }
        ui.onNodeWithText("▲").performTouchInput { up() }
        ui.onNodeWithText("PGDN").performTouchInput { click() }
        ui.runOnIdle { assertEquals(1, sent[1]) }
    }

    @Test
    fun pausingStopsAHeldKeyAndResumingDoesNotRestartIt() {
        var sent = 0
        val modifiers = TerminalModifiers()
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry(this)
        }
        ui.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        ui.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                MaterialTheme { JuiceSshKeyboardBar(modifiers, { sent++ }, {}, {}) }
            }
        }
        ui.mainClock.autoAdvance = false
        ui.onNodeWithText("▲").performTouchInput { down(center) }
        ui.mainClock.advanceTimeBy(700)
        ui.runOnIdle {
            assertTrue(sent >= 4)
            owner.lifecycle.currentState = Lifecycle.State.STARTED
        }
        ui.mainClock.advanceTimeBy(32)
        val paused = sent
        ui.mainClock.advanceTimeBy(600)
        ui.runOnIdle { assertEquals(paused, sent) }
        ui.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        ui.mainClock.advanceTimeBy(600)
        ui.runOnIdle { assertEquals(paused, sent) }
        ui.onNodeWithText("▲").performTouchInput { up() }
        ui.onNodeWithText("PGDN").performTouchInput { click() }
        ui.runOnIdle { assertEquals(paused + 1, sent) }
        ui.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.DESTROYED }
    }

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
