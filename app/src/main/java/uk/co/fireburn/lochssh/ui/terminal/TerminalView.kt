package uk.co.fireburn.lochssh.ui.terminal

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import uk.co.fireburn.lochssh.R
import uk.co.fireburn.lochssh.ssh.SshConnectionManager

// The system monospace family is not monospaced on every device, so the grid
// only lines up with a font we ship ourselves.
private val TerminalFont = FontFamily(
    Font(R.font.noto_sans_mono_regular, FontWeight.Normal),
    Font(R.font.noto_sans_mono_bold, FontWeight.Bold)
)

private val Background = Color(0xFF0D1B2A)
private val Cursor = Color(0xFFE9C46A)

private const val MIN_COLS = 20
private const val MIN_ROWS = 5
private const val REF_RUN = 64
private const val RESIZE_DELAY_MS = 150L

private class CellMetrics(val width: Float, val height: Float, val baseline: Float)

@Composable
fun TerminalView(
    modifier: Modifier = Modifier,
    manager: SshConnectionManager? = null,
    fontSize: TextUnit = 14.sp,
    onBuffer: (TerminalBuffer) -> Unit = {}
) {
    val buffer = remember { TerminalBuffer(MIN_COLS, MIN_ROWS) }
    val frame = remember { mutableIntStateOf(0) }
    val textMeasurer = rememberTextMeasurer()
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val style = remember(fontSize) {
        TextStyle(fontFamily = TerminalFont, fontSize = fontSize)
    }
    // Measured over a run so the advance keeps its fraction; rounding one
    // character would drift by a whole cell across a line.
    val metrics = remember(style, textMeasurer) {
        val run = textMeasurer.measure("M".repeat(REF_RUN), style)
        CellMetrics(
            width = run.size.width / REF_RUN.toFloat(),
            height = run.size.height.toFloat(),
            baseline = run.firstBaseline
        )
    }

    LaunchedEffect(Unit) { onBuffer(buffer) }

    // Fit the grid to the canvas and tell the remote side about it. Held back a
    // moment so a run of font size taps sends one size, not one for each tap.
    LaunchedEffect(canvasSize, metrics, manager) {
        if (canvasSize.width == 0 || canvasSize.height == 0) return@LaunchedEffect
        delay(RESIZE_DELAY_MS)
        val cols = (canvasSize.width / metrics.width).toInt().coerceAtLeast(MIN_COLS)
        val rows = (canvasSize.height / metrics.height).toInt().coerceAtLeast(MIN_ROWS)
        buffer.resize(cols, rows)
        manager?.resize(cols, rows)
        frame.intValue++
    }

    DisposableEffect(manager) {
        val m = manager ?: return@DisposableEffect onDispose {}
        val listener = SshConnectionManager.OutputListener { bytes, len ->
            buffer.feed(bytes, len)
            frame.intValue++
        }
        m.addOutputListener(listener)
        onDispose { m.removeOutputListener(listener) }
    }

    Canvas(modifier = modifier.onSizeChanged { canvasSize = it }) {
        // Read in the draw lambda: the root layer observes reads made while
        // drawing, so the canvas invalidates on every output chunk.
        frame.intValue
        drawTerminal(buffer, textMeasurer, style, metrics)
    }
}

private fun DrawScope.drawTerminal(
    buffer: TerminalBuffer,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    metrics: CellMetrics
) {
    drawRect(Background)
    buffer.read {
        if (buffer.cols == 0 || buffer.rows == 0) return@read
        val rows = minOf(buffer.rows, (size.height / metrics.height).toInt() + 1)

        for (row in 0 until rows) {
            val y = row * metrics.height
            drawCellBackgrounds(buffer, row, y, metrics)
            drawRowText(buffer, row, y, textMeasurer, style, metrics)
        }

        if (buffer.cursorVisible) {
            val cursorRow = buffer.cursorRow.coerceIn(0, buffer.rows - 1)
            val cursorCol = buffer.cursorCol.coerceIn(0, buffer.cols - 1)
            if (cursorRow < rows) {
                val origin = Offset(cursorCol * metrics.width, cursorRow * metrics.height)
                drawRect(Cursor, topLeft = origin, size = Size(metrics.width, metrics.height))
                val under = buffer.cellAt(cursorRow, cursorCol).ch
                if (under != ' ') {
                    drawText(textMeasurer, under.toString(), origin, style.copy(color = Background))
                }
            }
        }
    }
}

// Painted first so text lands on top of it.
private fun DrawScope.drawCellBackgrounds(
    buffer: TerminalBuffer,
    row: Int,
    y: Float,
    metrics: CellMetrics
) {
    var col = 0
    while (col < buffer.cols) {
        val colour = buffer.cellAt(row, col).bg
        if (colour == TerminalBuffer.DEFAULT_BG) {
            col++
            continue
        }
        val start = col
        while (col < buffer.cols && buffer.cellAt(row, col).bg == colour) col++
        drawRect(
            Color(colour),
            topLeft = Offset(start * metrics.width, y),
            size = Size((col - start) * metrics.width, metrics.height)
        )
    }
}

private fun DrawScope.drawRowText(
    buffer: TerminalBuffer,
    row: Int,
    y: Float,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    metrics: CellMetrics
) {
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
            Offset(runStart * metrics.width, y),
            style.copy(
                fontWeight = if (cell.bold) FontWeight.Bold else FontWeight.Normal,
                color = Color(cell.fg)
            )
        )
    }
}
