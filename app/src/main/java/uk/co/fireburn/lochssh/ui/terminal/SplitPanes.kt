package uk.co.fireburn.lochssh.ui.terminal

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.unit.dp

// Two panes of equal size: side by side when horizontal, stacked otherwise.
@Composable
internal fun SplitPanes(
    horizontal: Boolean,
    modifier: Modifier = Modifier,
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit
) {
    if (horizontal) {
        Row(modifier) {
            first(Modifier.weight(1f).fillMaxHeight())
            second(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(modifier) {
            first(Modifier.weight(1f).fillMaxWidth())
            second(Modifier.weight(1f).fillMaxWidth())
        }
    }
}

// Marks the pane that receives keyboard input and moves focus to it on any touch,
// without taking the touch away from the terminal's own gestures.
internal fun Modifier.splitFocus(focused: Boolean, accent: Color, onFocus: () -> Unit): Modifier =
    border(2.dp, if (focused) accent else Color.Transparent)
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    onFocus()
                }
            }
        }
