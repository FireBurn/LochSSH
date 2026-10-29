package uk.co.fireburn.lochssh.ui.terminal

import androidx.compose.ui.geometry.Offset

// Where the two ends of a selection are drawn and how a touch picks one up.
internal object SelectionMarks {
    enum class End { FIRST, LAST }

    private val reading = compareBy<TerminalBuffer.Point>({ it.row }, { it.col })

    fun ordered(a: TerminalBuffer.Point, b: TerminalBuffer.Point): Pair<TerminalBuffer.Point, TerminalBuffer.Point> =
        if (reading.compare(a, b) <= 0) a to b else b to a

    // The first end sits under the left edge of its cell and the last under the right edge of its cell.
    fun handleCentre(point: TerminalBuffer.Point, end: End, cellWidth: Float, cellHeight: Float, radius: Float): Offset =
        Offset((point.col + if (end == End.LAST) 1 else 0) * cellWidth, (point.row + 1) * cellHeight + radius)

    // The end a touch is closest to, if it is within reach of one.
    fun grabbed(touch: Offset, first: TerminalBuffer.Point, last: TerminalBuffer.Point,
        cellWidth: Float, cellHeight: Float, radius: Float, reach: Float): End? {
        val toFirst = (touch - handleCentre(first, End.FIRST, cellWidth, cellHeight, radius)).getDistance()
        val toLast = (touch - handleCentre(last, End.LAST, cellWidth, cellHeight, radius)).getDistance()
        return when {
            toFirst > reach && toLast > reach -> null
            toFirst <= toLast -> End.FIRST
            else -> End.LAST
        }
    }

    fun moved(origin: TerminalBuffer.Point, from: Offset, to: Offset, cellWidth: Float, cellHeight: Float,
        rows: Int, cols: Int): TerminalBuffer.Point = TerminalBuffer.Point(
        (origin.row + Math.round((to.y - from.y) / cellHeight)).coerceIn(0, rows - 1),
        (origin.col + Math.round((to.x - from.x) / cellWidth)).coerceIn(0, cols - 1)
    )
}
