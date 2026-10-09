package uk.co.fireburn.lochssh.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SplitPanesAndroidTest {
    @get:Rule val rule = createComposeRule()

    private fun show(horizontal: Boolean) = rule.setContent {
        Box(Modifier.fillMaxSize()) {
            SplitPanes(
                horizontal = horizontal,
                modifier = Modifier.fillMaxSize(),
                first = { Box(it.background(Color.Red).testTag("first")) },
                second = { Box(it.background(Color.Blue).testTag("second")) }
            )
        }
    }

    @Test
    fun horizontalPanesSitSideBySideAndShareTheWidth() {
        show(true)
        val first = rule.onNodeWithTag("first").getUnclippedBoundsInRoot()
        val second = rule.onNodeWithTag("second").getUnclippedBoundsInRoot()
        assertTrue(second.left >= first.right)
        assertEquals(first.top, second.top)
        assertEquals(first.width, second.width)
    }

    @Test
    fun verticalPanesAreStackedAndShareTheHeight() {
        show(false)
        val first = rule.onNodeWithTag("first").getUnclippedBoundsInRoot()
        val second = rule.onNodeWithTag("second").getUnclippedBoundsInRoot()
        assertTrue(second.top >= first.bottom)
        assertEquals(first.left, second.left)
        assertEquals(first.height, second.height)
    }

    @Test
    fun touchMovesFocusAndStillReachesThePane() {
        var focused = 0
        var clicked = 0
        rule.setContent {
            Box(Modifier.fillMaxSize()
                .splitFocus(false, Color.Green) { focused++ }
                .clickable { clicked++ }
                .testTag("pane"))
        }
        rule.onNodeWithTag("pane").performClick()
        rule.runOnIdle {
            assertEquals(1, focused)
            assertEquals(1, clicked)
        }
    }
}
