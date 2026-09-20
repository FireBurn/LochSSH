package uk.co.fireburn.lochssh.ui.terminal

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import uk.co.fireburn.lochssh.ssh.SshConnectionManager

@Composable
fun TerminalView(
    modifier: Modifier = Modifier,
    manager: SshConnectionManager? = null,
    initialCols: Int = 80,
    initialRows: Int = 24,
    onBuffer: (TerminalBuffer) -> Unit = {}
) {
    val buffer = remember { TerminalBuffer(initialCols, initialRows) }
    val frame = remember { mutableIntStateOf(0) }
    val textMeasurer = rememberTextMeasurer()

    LaunchedEffect(Unit) { onBuffer(buffer) }

    DisposableEffect(manager) {
        val m = manager ?: return@DisposableEffect onDispose {}
        val listener = SshConnectionManager.OutputListener { bytes, len ->
            buffer.feed(bytes, len)
            frame.intValue++
        }
        m.addOutputListener(listener)
        onDispose { m.removeOutputListener(listener) }
    }

    Canvas(modifier = modifier) {
        // Read in the draw lambda: the root layer observes reads made while
        // drawing, so the canvas invalidates on every output chunk.
        frame.intValue
        drawTerminal(buffer, textMeasurer)
    }
}

// Font size is chosen so the monospace advance equals the cell width,
// so whole style runs can be drawn as single text ops.
private fun DrawScope.drawTerminal(buffer: TerminalBuffer, textMeasurer: TextMeasurer) {
    if (buffer.cols == 0 || buffer.rows == 0) return
    val cellW = size.width / buffer.cols
    val cellH = size.height / buffer.rows

    val ref = textMeasurer.measure(
        "M",
        TextStyle(fontFamily = FontFamily.Monospace, fontSize = 100.sp)
    )
    val spFontSize = (cellW / (ref.size.width / 100f)).sp
    val line = textMeasurer.measure(
        "M",
        TextStyle(fontFamily = FontFamily.Monospace, fontSize = spFontSize)
    )
    val baselineOffset = (cellH - line.size.height) / 2f + line.firstBaseline

    for (row in 0 until buffer.rows) {
        val y = row * cellH + baselineOffset
        var col = 0
        while (col < buffer.cols) {
            val cell = buffer.cellAt(row, col)
            if (cell.ch == ' ') {
                col++
                continue
            }
            val runStart = col
            while (
                col < buffer.cols &&
                buffer.cellAt(row, col).ch != ' ' &&
                buffer.cellAt(row, col).fg == cell.fg &&
                buffer.cellAt(row, col).bold == cell.bold
            ) {
                col++
            }
            drawText(
                textMeasurer,
                buffer.rowText(row, runStart, col),
                Offset(runStart * cellW, y),
                TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (cell.bold) FontWeight.Bold else FontWeight.Normal,
                    fontSize = spFontSize,
                    color = Color(cell.fg)
                )
            )
        }
    }

    val (cr, cc) = buffer.cursor
    drawRect(
        color = Color.White.copy(alpha = 0.35f),
        topLeft = Offset(cc * cellW, cr * cellH),
        size = Size(cellW, cellH)
    )
}
