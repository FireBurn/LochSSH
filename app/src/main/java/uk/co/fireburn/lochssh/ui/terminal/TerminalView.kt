package uk.co.fireburn.lochssh.ui.terminal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.unit.dp
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
private const val MAX_LINK_ROWS = 40
private const val HANDLE_RADIUS = 7
private const val HANDLE_REACH = 36
private val SelectionMark = Color(0xFFE53935)

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
    onSelectionCopied: (String) -> Unit = {},
    onHint: (String?) -> Unit = {},
    onBuffer: (TerminalBuffer) -> Unit = {},
    onLinkTap: (List<String>) -> Unit = {},
    onMultiplexerScroll: (up: Boolean, lines: Int) -> Boolean = { _, _ -> false },
    onRemoteWheel: () -> Unit = {},
    onTap: () -> Unit = {},
    onRemoteScroll: (ByteArray) -> Unit = { manager?.write(it) }
) {
    val terminal = remember(manager) { manager?.terminal ?: TerminalSession(MIN_COLS, MIN_ROWS) }
    val buffer = terminal.buffer
    val sendScroll by rememberUpdatedState(onRemoteScroll)
    val openLink by rememberUpdatedState(onLinkTap)
    val scrollMultiplexer by rememberUpdatedState(onMultiplexerScroll)
    val remoteWheel by rememberUpdatedState(onRemoteWheel)
    val tapped by rememberUpdatedState(onTap)
    val copied by rememberUpdatedState(onSelectionCopied)
    val showHint by rememberUpdatedState(onHint)
    val frame = remember(buffer) { mutableIntStateOf(0) }
    val scrollOffset = remember(buffer) { mutableIntStateOf(0) }
    var selection by remember(buffer) { mutableStateOf<Selection?>(null) }
    // True once the finger has been lifted: the selection stays, can be adjusted and is copied again after each change.
    var settled by remember(buffer) { mutableStateOf(false) }
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
                                remoteWheel()
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
                    tapped()
                    if (selection != null) {
                        selection = null
                        settled = false
                        onSelectionChanged(null)
                        showHint(null)
                        return@awaitEachGesture
                    }
                    val links = buffer.read { linksAt(buffer, pointAt(up.position, metrics, buffer), scrollOffset.intValue) }
                    if (links.isNotEmpty()) openLink(links)
                }
            }
            .pointerInput(metrics.width, metrics.height, buffer) {
                val handleRadius = HANDLE_RADIUS.dp.toPx()
                val reach = HANDLE_REACH.dp.toPx()
                fun text(selected: Selection) = buffer.read {
                    buffer.selectedText(selected.start, selected.end, scrollOffset.intValue)
                }
                fun settle() {
                    val chosen = selection ?: return
                    settled = true
                    val copy = text(chosen)
                    onSelectionChanged(copy)
                    if (copy.isNotEmpty()) copied(copy)
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val held = selection
                    val grabbed = if (held != null && settled) {
                        val (first, last) = SelectionMarks.ordered(held.start, held.end)
                        SelectionMarks.grabbed(down.position, first, last, metrics.width, metrics.height,
                            handleRadius, reach)?.let { end -> Triple(first, last, end) }
                    } else null
                    if (grabbed != null) {
                        down.consume()
                        val (first, last, end) = grabbed
                        val fixed = if (end == SelectionMarks.End.FIRST) last else first
                        val origin = if (end == SelectionMarks.End.FIRST) first else last
                        showHint("Lift to copy the new selection")
                        drag(down.id) { change ->
                            change.consume()
                            val moving = buffer.read {
                                SelectionMarks.moved(origin, down.position, change.position, metrics.width,
                                    metrics.height, buffer.rows, buffer.cols)
                            }
                            selection = Selection(fixed, moving)
                        }
                        settle()
                        return@awaitEachGesture
                    }
                    // A press that is not held, or that moves, is left to scrolling and taps.
                    if (!awaitHold(down)) return@awaitEachGesture
                    down.consume()
                    val anchor = pointAt(down.position, metrics, buffer)
                    selection = Selection(anchor, anchor)
                    settled = false
                    onSelectionChanged(null)
                    showHint("Move your finger to choose the end, then lift to copy")
                    drag(down.id) { change ->
                        change.consume()
                        val moving = buffer.read {
                            SelectionMarks.moved(anchor, down.position, change.position, metrics.width,
                                metrics.height, buffer.rows, buffer.cols)
                        }
                        selection = Selection(anchor, moving)
                    }
                    settle()
                }
            }
    ) {
        // Read in the draw lambda: the root layer observes reads made while
        // drawing, so the canvas invalidates on every output chunk.
        frame.intValue
        clipRect {
            drawTerminal(buffer, textMeasurer, style, metrics, scrollOffset.intValue, selection, settled, theme)
        }
    }
}

// True when the pointer stays down and in place for the long-press time.
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.awaitHold(
    down: androidx.compose.ui.input.pointer.PointerInputChange
): Boolean {
    val slop = viewConfiguration.touchSlop
    val survived = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull false
            if (change.isConsumed || !change.pressed) return@withTimeoutOrNull false
            if ((change.position - down.position).getDistance() > slop) return@withTimeoutOrNull false
        }
        @Suppress("UNREACHABLE_CODE") true
    }
    return survived == null
}

internal fun isSpacingMark(text: String): Boolean = text.isNotEmpty() &&
    text.codePointCount(0, text.length) == 1 &&
    Character.getType(text.codePointAt(0)) == Character.COMBINING_SPACING_MARK.toInt()

internal fun linkAt(buffer: TerminalBuffer, point: TerminalBuffer.Point, scrollOffset: Int): String? =
    linksAt(buffer, point, scrollOffset, withPossible = false).firstOrNull()

// The addresses under a tap, longest first. Rows the terminal wrapped itself are joined for certain; a row that
// is full to its last column with text starting the next one may be a program's own line break, so the address
// is also offered cut at that row.
internal fun linksAt(buffer: TerminalBuffer, point: TerminalBuffer.Point, scrollOffset: Int,
    withPossible: Boolean = true): List<String> {
    if (point.row !in 0 until buffer.rows || point.col !in 0 until buffer.cols) return emptyList()
    val offset = scrollOffset.coerceIn(0, buffer.scrollbackSize)
    if (buffer.selectedText(point, point, offset).isBlank()) return emptyList()
    val historyOnly = buffer.isAlternateScreen && point.row < offset
    val oldest = if (buffer.isAlternateScreen && !historyOnly) offset else offset - buffer.scrollbackSize
    val newest = if (historyOnly) offset - 1 else buffer.rows + offset - 1
    fun wrapped(row: Int) = row in oldest until newest && buffer.visibleWrapWidth(row, offset) != null
    fun possible(row: Int) = withPossible && row in oldest until newest &&
        buffer.visibleCellAt(row, buffer.cols - 1, offset).text.isNotBlank() &&
        buffer.visibleCellAt(row + 1, 0, offset).text.isNotBlank()
    var first = point.row
    var guesses = 0
    while (first > oldest && (wrapped(first - 1) || (possible(first - 1) && guesses++ < MAX_LINK_ROWS))) first--
    var last = point.row
    guesses = 0
    while (last < newest && (wrapped(last) || (possible(last) && guesses++ < MAX_LINK_ROWS))) last++
    val text = StringBuilder()
    val breaks = ArrayList<Int>()
    var index = -1
    for (row in first..last) {
        val line = buffer.visibleRowText(row, 0, buffer.visibleWrapWidth(row, offset) ?: buffer.cols, offset)
        if (line.length > MAX_LINK_TEXT - text.length) return emptyList()
        if (row == point.row) index = text.length + buffer.visibleRowText(row, 0, point.col + 1, offset).length - 1
        text.append(line)
        if (row < last && buffer.visibleWrapWidth(row, offset) == null) breaks.add(text.length)
    }
    return TerminalLinks.candidatesAt(text.toString(), index, breaks)
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
    settled: Boolean,
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

        if (selection != null) drawSelectionMarks(selection, settled, metrics)

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

// A red bar at each end, and while settled a round handle under each that can be picked up and moved.
private fun DrawScope.drawSelectionMarks(selection: Selection, settled: Boolean, metrics: CellMetrics) {
    val (first, last) = SelectionMarks.ordered(selection.start, selection.end)
    val bar = 2.dp.toPx()
    val radius = HANDLE_RADIUS.dp.toPx()
    drawRect(SelectionMark, Offset(first.col * metrics.width - bar / 2, first.row * metrics.height), Size(bar, metrics.height))
    drawRect(SelectionMark, Offset((last.col + 1) * metrics.width - bar / 2, last.row * metrics.height), Size(bar, metrics.height))
    if (!settled) {
        // While the finger is down, outline the cell the selection started in so it is easy to find.
        drawRect(SelectionMark.copy(alpha = 0.35f), Offset(selection.start.col * metrics.width, selection.start.row * metrics.height),
            Size(metrics.width, metrics.height))
        return
    }
    drawCircle(SelectionMark, radius, SelectionMarks.handleCentre(first, SelectionMarks.End.FIRST, metrics.width, metrics.height, radius))
    drawCircle(SelectionMark, radius, SelectionMarks.handleCentre(last, SelectionMarks.End.LAST, metrics.width, metrics.height, radius))
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
        // A vowel sign has its own column, which keeps cursor positions in step with programs
        // that count it, but drawn alone it gets a dotted circle, so draw it with its letter.
        var markEnd = runStart + cell.columns
        val cluster = StringBuilder(cell.text)
        while (markEnd < buffer.cols && isSpacingMark(buffer.visibleCellAt(row, markEnd, scrollOffset).text)) {
            cluster.append(buffer.visibleCellAt(row, markEnd, scrollOffset).text)
            markEnd++
        }
        if (markEnd > runStart + cell.columns) {
            drawTerminalText(
                textMeasurer,
                cluster.toString(),
                Offset(runStart * metrics.width, y),
                style.copy(
                    fontWeight = if (cell.bold) FontWeight.Bold else FontWeight.Normal,
                    color = Color(theme.color(cell.fg))
                )
            )
            col = markEnd
            continue
        }
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
