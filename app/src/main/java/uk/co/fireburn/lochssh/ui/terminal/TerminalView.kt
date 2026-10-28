package uk.co.fireburn.lochssh.ui.terminal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import uk.co.fireburn.lochssh.ssh.SshConnectionManager

private val SelectionColour = Color(0x8855A878)

private const val MIN_COLS = 20
private const val MIN_ROWS = 5
private const val REF_RUN = 64
private const val RESIZE_DELAY_MS = 150L

private class CellMetrics(val width: Float, val height: Float, val baseline: Float)
private const val MAX_LINK_TEXT = 65_536

private data class Selection(val start: TerminalBuffer.Point, val end: TerminalBuffer.Point)

@Composable
fun TerminalView(
    modifier: Modifier = Modifier,
    manager: SshConnectionManager? = null,
    fontSize: TextUnit = 14.sp,
    typeface: TerminalTypeface = TerminalTypeface.NOTO,
    wrapLines: Boolean = true,
    theme: TerminalTheme = TerminalTheme.LOCH,
    scrollToLiveKey: Int = 0,
    clearSelectionKey: Int = 0,
    onScrollOffsetChanged: (Int) -> Unit = {},
    onSelectionChanged: (String?) -> Unit = {},
    onBuffer: (TerminalBuffer) -> Unit = {},
    onLinkTap: (String) -> Unit = {},
    onMultiplexerScroll: (up: Boolean, lines: Int) -> Boolean = { _, _ -> false },
    onRemoteScroll: (ByteArray) -> Unit = { manager?.write(it) }
) {
    val terminal = remember(manager) { manager?.terminal ?: TerminalSession(MIN_COLS, MIN_ROWS) }
    val buffer = terminal.buffer
    val sendScroll by rememberUpdatedState(onRemoteScroll)
    val openLink by rememberUpdatedState(onLinkTap)
    val scrollMultiplexer by rememberUpdatedState(onMultiplexerScroll)
    val frame = remember(buffer) { mutableIntStateOf(0) }
    val scrollOffset = remember(buffer) { mutableIntStateOf(0) }
    var selection by remember(buffer) { mutableStateOf<Selection?>(null) }
    val textMeasurer = rememberTextMeasurer()
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val style = remember(fontSize, typeface) {
        TextStyle(fontFamily = typeface.family, fontSize = fontSize)
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
    LaunchedEffect(buffer, wrapLines) {
        buffer.setLineWrapping(wrapLines)
        frame.intValue++
    }
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
        val cols = (canvasSize.width / metrics.width).toInt().coerceAtLeast(1)
        val rows = (canvasSize.height / metrics.height).toInt().coerceAtLeast(1)
        if (buffer.read { buffer.cols == cols && buffer.rows == rows }) return@LaunchedEffect
        selection = null
        onSelectionChanged(null)
        buffer.resize(cols, rows)
        scrollOffset.intValue = buffer.read {
            scrollOffset.intValue.coerceAtMost(buffer.scrollbackSize)
        }
        manager?.resize(cols, rows)
        frame.intValue++
    }

    LaunchedEffect(terminal) {
        var before = buffer.read { buffer.totalScrolled }
        terminal.revision.collect {
            val total = buffer.read { buffer.totalScrolled }
            if (scrollOffset.intValue > 0) {
                val added = (total - before).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                scrollOffset.intValue = buffer.read {
                    (scrollOffset.intValue.toLong() + added).coerceAtMost(buffer.scrollbackSize.toLong()).toInt()
                }
            }
            before = total
            frame.intValue++
        }
    }

    Canvas(
        modifier = modifier
            .onSizeChanged { canvasSize = it }
            .pointerInput(metrics.width, metrics.height, buffer, manager) {
                var dragged = 0f
                detectVerticalDragGestures(
                    onDragEnd = { dragged = 0f },
                    onDragCancel = { dragged = 0f },
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        dragged += amount
                        val lines = (dragged / metrics.height).toInt()
                        if (lines != 0) {
                            val wheel = buffer.read {
                                val point = pointAt(change.position, metrics, buffer)
                                TerminalScroll.remoteInput(buffer, lines > 0, point.col, point.row,
                                    scrollOffset.intValue)
                            }
                            if (wheel != null) {
                                repeat(kotlin.math.abs(lines).coerceAtMost(32)) { sendScroll(wheel) }
                            } else if (scrollOffset.intValue == 0 &&
                                scrollMultiplexer(lines > 0, kotlin.math.abs(lines))) {
                                // The session scrolls itself.
                            } else {
                                scrollOffset.intValue = buffer.read {
                                    (scrollOffset.intValue + lines).coerceIn(0, buffer.scrollbackSize)
                                }
                            }
                            dragged -= lines * metrics.height
                        }
                    }
                )
            }
            .pointerInput(metrics.width, metrics.height, buffer) {
                // Does not consume, so taps still reach whatever shows the keyboard.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                    if ((up.position - down.position).getDistance() > viewConfiguration.touchSlop) return@awaitEachGesture
                    val link = buffer.read { linkAt(buffer, pointAt(up.position, metrics, buffer), scrollOffset.intValue) }
                    if (link != null) openLink(link)
                }
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
        clipRect {
            drawTerminal(buffer, textMeasurer, style, metrics, scrollOffset.intValue, selection, theme)
        }
    }
}

internal fun linkAt(buffer: TerminalBuffer, point: TerminalBuffer.Point, scrollOffset: Int): String? {
    if (point.row !in 0 until buffer.rows || point.col !in 0 until buffer.cols) return null
    val offset = scrollOffset.coerceIn(0, buffer.scrollbackSize)
    if (buffer.selectedText(point, point, offset).isBlank()) return null
    val historyOnly = buffer.isAlternateScreen && point.row < offset
    val oldest = if (buffer.isAlternateScreen && !historyOnly) offset else offset - buffer.scrollbackSize
    val newest = if (historyOnly) offset - 1 else buffer.rows + offset - 1
    var first = point.row
    while (first > oldest && buffer.visibleWrapWidth(first - 1, offset) != null) first--
    var last = point.row
    while (last < newest && buffer.visibleWrapWidth(last, offset) != null) last++
    if (buffer.visibleWrapWidth(last, offset) != null) return null
    val text = StringBuilder()
    var index = -1
    for (row in first..last) {
        val line = buffer.visibleRowText(row, 0, buffer.visibleWrapWidth(row, offset) ?: buffer.cols, offset)
        if (line.length > MAX_LINK_TEXT - text.length) return null
        if (row == point.row) index = text.length + buffer.visibleRowText(row, 0, point.col + 1, offset).length - 1
        text.append(line)
    }
    return TerminalLinks.at(text.toString(), index)
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
    selection: Selection?,
    theme: TerminalTheme
) {
    drawRect(Color(theme.background))
    buffer.read {
        if (buffer.cols == 0 || buffer.rows == 0) return@read
        val rows = minOf(buffer.rows, (size.height / metrics.height).toInt() + 1)

        for (row in 0 until rows) {
            val y = row * metrics.height
            drawCellBackgrounds(buffer, row, y, metrics, scrollOffset, theme)
            if (selection != null) drawSelection(row, selection, buffer.cols, y, metrics)
            drawRowText(buffer, row, y, textMeasurer, style, metrics, scrollOffset, theme)
        }

        if (buffer.cursorVisible && scrollOffset == 0) {
            val cursorRow = buffer.cursorRow.coerceIn(0, buffer.rows - 1)
            var cursorCol = buffer.cursorCol.coerceIn(0, buffer.cols - 1)
            if (buffer.cellAt(cursorRow, cursorCol).columns == 0) cursorCol = (cursorCol - 1).coerceAtLeast(0)
            if (cursorRow < rows) {
                val origin = Offset(cursorCol * metrics.width, cursorRow * metrics.height)
                val cell = buffer.cellAt(cursorRow, cursorCol)
                drawRect(Color(theme.cursor), topLeft = origin,
                    size = Size(metrics.width * cell.columns, metrics.height))
                val under = cell.text
                if (under != " ") {
                    drawTerminalText(textMeasurer, under, origin, style.copy(color = Color(theme.background)))
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
    scrollOffset: Int,
    theme: TerminalTheme
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
            Color(theme.color(colour)),
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
    scrollOffset: Int,
    theme: TerminalTheme
) {
    var col = 0
    while (col < buffer.cols) {
        val cell = buffer.visibleCellAt(row, col, scrollOffset)
        if (cell.text == " " || cell.columns == 0) {
            col++
            continue
        }
        val runStart = col
        col++
        while (
            col < buffer.cols &&
            cell.text.length == 1 && cell.text[0] in '!'..'~' &&
            buffer.visibleCellAt(row, col, scrollOffset).text.length == 1 &&
            buffer.visibleCellAt(row, col, scrollOffset).text[0] in '!'..'~' &&
            buffer.visibleCellAt(row, col, scrollOffset).fg == cell.fg &&
            buffer.visibleCellAt(row, col, scrollOffset).bold == cell.bold
        ) {
            col++
        }
        drawTerminalText(
            textMeasurer,
            buffer.visibleRowText(row, runStart, col, scrollOffset),
            Offset(runStart * metrics.width, y),
            style.copy(
                fontWeight = if (cell.bold) FontWeight.Bold else FontWeight.Normal,
                color = Color(theme.color(cell.fg))
            )
        )
    }
}
