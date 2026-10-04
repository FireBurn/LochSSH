package uk.co.fireburn.lochssh.ui.terminal

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TerminalScrollAndroidTest {
    @get:Rule val ui = createComposeRule()
    private var buffer: TerminalBuffer? = null
    private var offset = 0
    private val sent = mutableListOf<String>()

    @Test fun touchScrollingUsesTheRequestedArrowModeAndStopsWhenDisabled() {
        showTerminal()
        send("\u001B[?1049;1007;1h")
        dragDown()
        ui.runOnIdle {
            assertTrue(sent.isNotEmpty())
            assertTrue(sent.all { it == "\u001BOA" })
            assertEquals(0, offset)
        }
        send("\u001B[?1007l")
        val previous = sent.size
        dragDown()
        ui.runOnIdle { assertEquals(previous, sent.size) }
    }

    @Test fun mouseReportsTakePriorityAndOrdinaryScrollingOnlyMovesHistory() {
        showTerminal()
        send("\u001B[?1049;1007;1000;1006h")
        dragDown()
        ui.runOnIdle {
            assertTrue(sent.isNotEmpty())
            assertTrue(sent.all { it.startsWith("\u001B[<64;") && it.endsWith("M") })
        }
        send("\u001B[?1000;1049l" + "history\r\n".repeat(100))
        val previous = sent.size
        dragDown()
        ui.waitUntil(5_000) { offset > 0 }
        ui.runOnIdle { assertEquals(previous, sent.size) }
    }

    private fun showTerminal() {
        ui.setContent {
            MaterialTheme {
                TerminalView(Modifier.size(300.dp, 200.dp).testTag("terminal"),
                    onBuffer = { buffer = it }, onScrollOffsetChanged = { offset = it },
                    onRemoteScroll = { sent.add(it.toString(Charsets.US_ASCII)) })
            }
        }
        ui.waitUntil(5_000) { buffer?.cols?.let { it > 20 } == true }
    }

    private fun send(text: String) = ui.runOnIdle {
        val bytes = text.toByteArray(Charsets.UTF_8)
        requireNotNull(buffer).feed(bytes, bytes.size)
    }

    private fun dragDown() {
        ui.onNodeWithTag("terminal").performTouchInput {
            down(Offset(center.x, height * 0.2f))
            moveTo(Offset(center.x, height * 0.8f), delayMillis = 100)
            up()
        }
    }
}
