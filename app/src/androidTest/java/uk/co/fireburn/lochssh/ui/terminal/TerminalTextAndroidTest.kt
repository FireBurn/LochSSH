package uk.co.fireburn.lochssh.ui.terminal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TerminalTextAndroidTest {
    @get:Rule
    val ui = createComposeRule()

    @Test
    fun drawsTextBeyondCanvasEdgesWithoutInvalidConstraints() {
        var draws = 0
        ui.setContent {
            val measurer = rememberTextMeasurer()
            Canvas(Modifier.size(20.dp).testTag("terminal")) {
                val style = TextStyle(fontSize = 14.sp)
                for (origin in listOf(Offset.Zero, Offset(size.width + 1, 0f),
                    Offset(0f, size.height + 1), Offset(size.width + 1, size.height + 1))) {
                    drawTerminalText(measurer, "tmux status", origin, style)
                }
                draws++
            }
        }
        ui.onNodeWithTag("terminal").captureToImage()
        ui.runOnIdle { assertEquals(true, draws > 0) }
    }
}
