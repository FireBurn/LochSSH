package uk.co.fireburn.lochssh.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalCharsetsTest {
    @Test
    fun specialGraphicsMapEveryCharacterWithoutChangingOtherText() {
        val buffer = TerminalBuffer(40, 4)
        buffer.send("\u001B(0" + ('_'..'~').joinToString("") + " ABC")
        assertEquals(" ◆▒␉␌␍␊°±␤␋┘┐┌└┼⎺⎻─⎼⎽├┤┴┬│≤≥π≠£· ABC",
            buffer.rowText(0, 0, 36))
        buffer.send("\u001B(Bq")
        assertEquals('q', buffer.cellAt(0, 36).ch)
    }

    @Test
    fun shiftedGraphicsDrawBordersAndReturnToAscii() {
        val buffer = TerminalBuffer(12, 4)
        buffer.send("\u001B)0\u000Elqqk\r\nx  x\r\nmqqj\u000Fq")
        assertEquals("┌──┐", buffer.rowText(0, 0, 4))
        assertEquals("│  │", buffer.rowText(1, 0, 4))
        assertEquals("└──┘q", buffer.rowText(2, 0, 5))
    }

    @Test
    fun characterSelectionsSurviveSplitReadsAndDoNotTranslateUnicode() {
        val buffer = TerminalBuffer(12, 4)
        buffer.send("\u001B(")
        buffer.send("A#\u001B(0")
        val text = "é─".toByteArray(Charsets.UTF_8)
        for (byte in text) buffer.feed(byteArrayOf(byte), 1)
        buffer.send("q")
        assertEquals("£é──", buffer.rowText(0, 0, 4))
    }

    @Test
    fun singleShiftsApplyOnceAndLockingShiftsRemainActive() {
        val buffer = TerminalBuffer(12, 4)
        buffer.send("\u001B*0\u001B+A\u001BNqq\u001BO##\u001Bnq\u001Bo#\u000Fq")
        assertEquals("─q£#─£q", buffer.rowText(0, 0, 7))
    }

    @Test
    fun savedCursorRetainsCharacterSlotsAndPendingSingleShift() {
        val buffer = TerminalBuffer(12, 4)
        buffer.send("\u001B)0\u000E\u001B7\u001B)B\u000F\u001B8q")
        assertEquals('─', buffer.cellAt(0, 0).ch)
        buffer.send("\u001B*0\u000F\u001BN\u001B7\u001B*Bq\u001B8qq")
        assertEquals("──q", buffer.rowText(0, 0, 3))
    }

    @Test
    fun screenChangesAndResizeKeepTheShellCharacterSets() {
        val buffer = TerminalBuffer(12, 4)
        buffer.send("\u001B)0\u000Eq\u001B[?1049h\u001B)B\u000Feditor")
        buffer.resize(8, 3)
        buffer.send("\u001B[?1049lq")
        assertEquals("──", buffer.rowText(0, 0, 2))
        buffer.send("\u001B[!pq")
        assertEquals('q', buffer.cellAt(0, 2).ch)
        buffer.send("\u001B(0\u001Bcq")
        assertEquals("q", buffer.rowText(0, 0, 8).trimEnd())
    }

    @Test
    fun unknownSelectionsAndCancelledCommandsDoNotChangeTheSet() {
        val buffer = TerminalBuffer(12, 4)
        buffer.send("\u001B(0\u001B(Zq\u001B(\u0018x\u001B(Bq")
        assertEquals("─│q", buffer.rowText(0, 0, 3))
    }

    private fun TerminalBuffer.send(text: String) {
        val bytes = text.toByteArray(Charsets.US_ASCII)
        feed(bytes, bytes.size)
    }
}
