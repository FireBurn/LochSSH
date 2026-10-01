package uk.co.fireburn.lochssh.ui.terminal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText

internal fun DrawScope.drawTerminalText(
    measurer: TextMeasurer,
    text: String,
    origin: Offset,
    style: TextStyle
) {
    val layout = measurer.measure(text, style, softWrap = false)
    drawText(layout, topLeft = origin)
}
