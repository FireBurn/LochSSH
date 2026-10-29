package uk.co.fireburn.lochssh.ui.terminal

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TerminalSelectionAndroidTest {
    @get:Rule val ui = createComposeRule()
    private var buffer: TerminalBuffer? = null
    private val copies = mutableListOf<String>()
    private val hints = mutableListOf<String?>()
    private var selected: String? = "unset"

    private fun showTerminal() {
        ui.setContent { MaterialTheme {
            TerminalView(Modifier.size(300.dp, 200.dp).testTag("terminal"),
                onBuffer = { buffer = it },
                onSelectionChanged = { selected = it },
                onSelectionCopied = { copies.add(it) },
                onHint = { hints.add(it) })
        } }
        ui.waitUntil(5_000) { buffer?.cols?.let { it > 20 } == true }
        ui.runOnIdle {
            val bytes = "alpha beta gamma\r\nsecond line".toByteArray()
            requireNotNull(buffer).feed(bytes, bytes.size)
        }
    }

    private fun cellCentre(touch: androidx.compose.ui.test.TouchInjectionScope, row: Int, col: Int): Offset {
        val b = requireNotNull(buffer)
        return Offset((col + 0.5f) * touch.width / b.cols, (row + 0.5f) * touch.height / b.rows)
    }

    @Test fun holdingThenMovingAndLiftingCopiesTheTextBetweenTheTwoPoints() {
        showTerminal()
        ui.onNodeWithTag("terminal").performTouchInput {
            down(cellCentre(this, 0, 6))
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 200)
            moveTo(cellCentre(this, 0, 9), delayMillis = 50)
            up()
        }
        ui.runOnIdle {
            assertEquals(listOf("beta"), copies)
            assertEquals("beta", selected)
            assertTrue(hints.any { it?.contains("Move your finger") == true })
        }
    }

    @Test fun aQuickTouchDoesNotStartASelection() {
        showTerminal()
        ui.onNodeWithTag("terminal").performTouchInput { click(cellCentre(this, 0, 6)) }
        ui.runOnIdle { assertTrue(copies.isEmpty()) }
    }

    @Test fun tappingElsewhereClearsTheSelection() {
        showTerminal()
        ui.onNodeWithTag("terminal").performTouchInput {
            down(cellCentre(this, 0, 0))
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 200)
            moveTo(cellCentre(this, 0, 4), delayMillis = 50)
            up()
        }
        ui.runOnIdle { assertEquals("alpha", selected) }
        ui.onNodeWithTag("terminal").performTouchInput { click(cellCentre(this, 1, 3)) }
        ui.runOnIdle { assertNull(selected) }
    }

    @Test fun draggingAHandleChangesTheSelectionAndCopiesAgain() {
        showTerminal()
        val b = requireNotNull(buffer)
        ui.onNodeWithTag("terminal").performTouchInput {
            down(cellCentre(this, 0, 0))
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 200)
            moveTo(cellCentre(this, 0, 4), delayMillis = 50)
            up()
        }
        ui.runOnIdle { assertEquals("alpha", selected) }
        ui.onNodeWithTag("terminal").performTouchInput {
            val handleRadius = 7.dp.toPx()
            // The end handle sits under the right edge of the last selected cell.
            val handle = Offset(5f * width / b.cols, (1f) * height / b.rows + handleRadius)
            down(handle)
            moveTo(Offset(handle.x + 6f * width / b.cols, handle.y), delayMillis = 50)
            up()
        }
        ui.runOnIdle {
            assertEquals("alpha beta", selected)
            assertEquals(listOf("alpha", "alpha beta"), copies)
        }
    }
}
