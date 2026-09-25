package uk.co.fireburn.lochssh.ui.terminal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.ui.input.pointer.pointerInput
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
private val SelectionColour = Color(0x8855A878)

private const val MIN_COLS = 20
private const val MIN_ROWS = 5
private const val REF_RUN = 64
private const val RESIZE_DELAY_MS = 150L

private class CellMetrics(val width: Float, val height: Float, val baseline: Float)
private data class Selection(val start: TerminalBuffer.Point, val end: TerminalBuffer.Point)

@Composable
fun TerminalView(
    modifier: Modifier = Modifier,
    manager: SshConnectionManager? = null,
    fontSize: TextUnit = 14.sp,
    scrollToLiveKey: Int = 0,
    clearSelectionKey: Int = 0,
    onScrollOffsetChanged: (Int) -> Unit = {},
    onSelectionChanged: (String?) -> Unit = {},
    onBuffer: (TerminalBuffer) -> Unit = {}
) {
    val buffer = remember(manager) { TerminalBuffer(MIN_COLS, MIN_ROWS) }
    val frame = remember(buffer) { mutableIntStateOf(0) }
    val scrollOffset = remember(buffer) { mutableIntStateOf(0) }
    var selection by remember(buffer) { mutableStateOf<Selection?>(null) }
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

    LaunchedEffect(buffer) { onBuffer(buffer) }
    LaunchedEffect(manager) { scrollOffset.intValue = 0 }
    LaunchedEffect(scrollToLiveKey) { scrollOffset.intValue = 0 }
    LaunchedEffect(scrollOffset.intValue) { onScrollOffsetChanged(scrollOffset.intValue) }
    LaunchedEffect(clearSelectionKey, buffer, scrollOffset.intValue) {
        selection = null
        onSelectionChanged(null)
    }

    // Fit the grid to the canvas and tell the remote side about it. Held back a
    // moment so a run of font size taps sends one size, not one for each tap.
    LaunchedEffect(canvasSize, metrics, manager) {
        if (canvasSize.width == 0 || canvasSize.height == 0) return@LaunchedEffect
        delay(RESIZE_DELAY_MS)
        val cols = (canvasSize.width / metrics.width).toInt().coerceAtLeast(MIN_COLS)
        val rows = (canvasSize.height / metrics.height).toInt().coerceAtLeast(MIN_ROWS)
        buffer.resize(cols, rows)
        scrollOffset.intValue = buffer.read {
            scrollOffset.intValue.coerceAtMost(buffer.scrollbackSize)
        }
        manager?.resize(cols, rows)
        frame.intValue++
    }

    DisposableEffect(manager) {
        val m = manager ?: return@DisposableEffect onDispose {}
        val listener = SshConnectionManager.OutputListener { bytes, len ->
            val before = buffer.read { buffer.totalScrolled }
            buffer.feed(bytes, len)
            if (scrollOffset.intValue > 0) {
                val added = buffer.read { (buffer.totalScrolled - before).toInt() }
                scrollOffset.intValue = buffer.read {
                    (scrollOffset.intValue + added).coerceAtMost(buffer.scrollbackSize)
                }
            }
            frame.intValue++
        }
        m.addOutputListener(listener)
        onDispose { m.removeOutputListener(listener) }
    }

    Canvas(
        modifier = modifier
            .onSizeChanged { canvasSize = it }
            .pointerInput(metrics.height) {
                var dragged = 0f
                detectVerticalDragGestures(
                    onDragEnd = { dragged = 0f },
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        dragged += amount
                        val lines = (dragged / metrics.height).toInt()
                        if (lines != 0) {
                            scrollOffset.intValue = buffer.read {
                                (scrollOffset.intValue + lines).coerceIn(0, buffer.scrollbackSize)
                            }
                            dragged -= lines * metrics.height
                        }
                    }
                )
            }
            .pointerInput(metrics.width, metrics.height, buffer) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { position ->
                        val point = pointAt(position, metrics, buffer)
                        selection = Selection(point, point)
                        onSelectionChanged(null)
                    },
                    onDragEnd = {
                        selection?.let { selected ->
                            onSelectionChanged(buffer.read {
                                buffer.selectedText(
                                    selected.start, selected.end, scrollOffset.intValue
                                )
                            })
                        }
                    },
                    onDragCancel = {
                        selection = null
                        onSelectionChanged(null)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        selection = selection?.copy(end = pointAt(change.position, metrics, buffer))
                    }
                )
            }
    ) {
        // Read in the draw lambda: the root layer observes reads made while
        // drawing, so the canvas invalidates on every output chunk.
        frame.intValue
        drawTerminal(buffer, textMeasurer, style, metrics, scrollOffset.intValue, selection)
    }
}

private fun pointAt(position: Offset, metrics: CellMetrics, buffer: TerminalBuffer):
    TerminalBuffer.Point = buffer.read {
    TerminalBuffer.Point(
        (position.y / metrics.height).toInt().coerceIn(0, buffer.rows - 1),
        (position.x / metrics.width).toInt().coerceIn(0, buffer.cols - 1)
    )
}

private fun DrawScope.drawTerminal(
    buffer: TerminalBuffer,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    metrics: CellMetrics,
    scrollOffset: Int,
    selection: Selection?
) {
    drawRect(Background)
    buffer.read {
        if (buffer.cols == 0 || buffer.rows == 0) return@read
        val rows = minOf(buffer.rows, (size.height / metrics.height).toInt() + 1)

        for (row in 0 until rows) {
            val y = row * metrics.height
            drawCellBackgrounds(buffer, row, y, metrics, scrollOffset)
            if (selection != null) drawSelection(row, selection, buffer.cols, y, metrics)
            drawRowText(buffer, row, y, textMeasurer, style, metrics, scrollOffset)
        }

        if (buffer.cursorVisible && scrollOffset == 0) {
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

private fun DrawScope.drawSelection(
    row: Int,
    selection: Selection,
    cols: Int,
    y: Float,
    metrics: CellMetrics
) {
    val first = minOf(selection.start, selection.end, compareBy(TerminalBuffer.Point::row, TerminalBuffer.Point::col))
    val last = maxOf(selection.start, selection.end, compareBy(TerminalBuffer.Point::row, TerminalBuffer.Point::col))
    if (row !in first.row..last.row) return
    val from = if (row == first.row) first.col else 0
    val until = if (row == last.row) last.col + 1 else cols
    drawRect(
        SelectionColour,
        topLeft = Offset(from * metrics.width, y),
        size = Size((until - from) * metrics.width, metrics.height)
    )
}

// Painted first so text lands on top of it.
private fun DrawScope.drawCellBackgrounds(
    buffer: TerminalBuffer,
    row: Int,
    y: Float,
    metrics: CellMetrics,
    scrollOffset: Int
) {
    var col = 0
    while (col < buffer.cols) {
        val colour = buffer.visibleCellAt(row, col, scrollOffset).bg
        if (colour == TerminalBuffer.DEFAULT_BG) {
            col++
            continue
        }
        val start = col
        while (col < buffer.cols && buffer.visibleCellAt(row, col, scrollOffset).bg == colour) col++
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
    metrics: CellMetrics,
    scrollOffset: Int
) {
    var col = 0
    while (col < buffer.cols) {
        val cell = buffer.visibleCellAt(row, col, scrollOffset)
        if (cell.ch == ' ') {
            col++
            continue
        }
        val runStart = col
        while (
            col < buffer.cols &&
            buffer.visibleCellAt(row, col, scrollOffset).ch != ' ' &&
            buffer.visibleCellAt(row, col, scrollOffset).fg == cell.fg &&
            buffer.visibleCellAt(row, col, scrollOffset).bold == cell.bold
        ) {
            col++
        }
        drawText(
            textMeasurer,
            buffer.visibleRowText(row, runStart, col, scrollOffset),
            Offset(runStart * metrics.width, y),
            style.copy(
                fontWeight = if (cell.bold) FontWeight.Bold else FontWeight.Normal,
                color = Color(cell.fg)
            )
        )
    }
}
