package uk.co.fireburn.lochssh.ui.terminal

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uk.co.fireburn.lochssh.ui.terminal.SelectionMarks.End

class SelectionMarksTest {
    private val a = TerminalBuffer.Point(2, 5)
    private val b = TerminalBuffer.Point(4, 1)

    @Test
    fun endsAreOrderedInReadingOrder() {
        assertEquals(a to b, SelectionMarks.ordered(a, b))
        assertEquals(a, SelectionMarks.ordered(b, a).first)
        assertEquals(b, SelectionMarks.ordered(b, a).second)
        assertEquals(TerminalBuffer.Point(1, 1), SelectionMarks.ordered(TerminalBuffer.Point(1, 3), TerminalBuffer.Point(1, 1)).first)
    }

    @Test
    fun aTouchPicksTheNearerHandleOrNone() {
        val w = 10f; val h = 20f; val r = 6f; val reach = 40f
        val first = SelectionMarks.handleCentre(a, End.FIRST, w, h, r)
        val last = SelectionMarks.handleCentre(b, End.LAST, w, h, r)
        assertEquals(End.FIRST, SelectionMarks.grabbed(first + Offset(5f, 3f), a, b, w, h, r, reach))
        assertEquals(End.LAST, SelectionMarks.grabbed(last + Offset(-4f, 2f), a, b, w, h, r, reach))
        assertNull(SelectionMarks.grabbed(Offset(400f, 400f), a, b, w, h, r, reach))
    }

    @Test
    fun movingFollowsTheFingerByWholeCellsAndStaysOnTheScreen() {
        val start = TerminalBuffer.Point(3, 3)
        val p = SelectionMarks.moved(start, Offset(100f, 100f), Offset(130f, 60f), 10f, 20f, 10, 40)
        assertEquals(TerminalBuffer.Point(1, 6), p)
        val clamped = SelectionMarks.moved(start, Offset(0f, 0f), Offset(-500f, 900f), 10f, 20f, 10, 40)
        assertEquals(TerminalBuffer.Point(9, 0), clamped)
    }
}
