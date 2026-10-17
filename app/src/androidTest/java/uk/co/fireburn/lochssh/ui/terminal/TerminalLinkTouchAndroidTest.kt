package uk.co.fireburn.lochssh.ui.terminal

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TerminalLinkTouchAndroidTest {
    @get:Rule val ui = createComposeRule()
    private var buffer: TerminalBuffer? = null
    private val opened = mutableListOf<String>()

    @Test fun tappingAWrappedUrlPassesItsWholeValueToTheConfirmation() {
        showTerminal()
        val url = "https://a.test/" + "x".repeat(requireNotNull(buffer).cols * 2)
        send(url)
        ui.onNodeWithTag("terminal").performTouchInput {
            val rowHeight = height.toFloat() / requireNotNull(buffer).rows
            click(Offset(width * 0.1f, rowHeight * 1.5f))
        }
        ui.runOnIdle { assertEquals(listOf(url), opened) }
    }

    @Test fun draggingFromALinkDoesNotOpenIt() {
        showTerminal()
        send("https://a.test/path")
        ui.onNodeWithTag("terminal").performTouchInput {
            val rowHeight = height.toFloat() / requireNotNull(buffer).rows
            down(Offset(width * 0.1f, rowHeight * 0.5f))
            moveTo(Offset(width * 0.1f, height * 0.8f), delayMillis = 100)
            up()
        }
        ui.runOnIdle { assertTrue(opened.isEmpty()) }
    }

    @Test fun aTapOnOrdinaryTextDoesNotOpenABrowser() {
        showTerminal()
        send("ordinary text")
        ui.onNodeWithTag("terminal").performTouchInput {
            click(Offset(width * 0.1f, height.toFloat() / requireNotNull(buffer).rows * 0.5f))
        }
        ui.runOnIdle { assertTrue(opened.isEmpty()) }
    }

    @Test fun tappingALinkAlsoFocusesTheSplitPane() {
        var focused = false
        showTerminal(Modifier.splitFocus(false, Color.Green) { focused = true })
        val url = "https://a.test/path"
        send(url)
        ui.onNodeWithTag("terminal").performTouchInput {
            click(Offset(width * 0.1f, height.toFloat() / requireNotNull(buffer).rows * 0.5f))
        }
        ui.runOnIdle {
            assertTrue(focused)
            assertEquals(listOf(url), opened)
        }
    }

    private fun showTerminal(modifier: Modifier = Modifier) {
        ui.setContent { MaterialTheme {
            TerminalView(modifier.size(300.dp, 200.dp).testTag("terminal"),
                onBuffer = { buffer = it }, onLinkTap = { opened.add(it) })
        } }
        ui.waitUntil(5_000) { buffer?.cols?.let { it > 20 } == true }
    }

    private fun send(text: String) = ui.runOnIdle {
        val bytes = text.toByteArray()
        requireNotNull(buffer).feed(bytes, bytes.size)
    }
}
