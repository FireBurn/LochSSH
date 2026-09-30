package uk.co.fireburn.lochssh.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalWideTest {
    @Test
    fun wideCharactersReserveTwoColumnsAndCopyOnceFromEitherHalf() {
        val buffer = TerminalBuffer(8, 4)
        buffer.send("a界b")
        assertEquals(0 to 4, buffer.cursor)
        assertEquals(2, buffer.cellAt(0, 1).columns)
        assertEquals(0, buffer.cellAt(0, 2).columns)
        assertEquals("a界b", buffer.rowText(0, 0, 4))
        assertEquals("a界b\n", buffer.transcript())
        for (col in 1..2) {
            val point = TerminalBuffer.Point(0, col)
            assertEquals("界", buffer.selectedText(point, point, 0))
        }
    }

    @Test
    fun fullwidthAndSupplementaryCharactersUseTheSameGridRules() {
        val buffer = TerminalBuffer(8, 4)
        buffer.send("Ａ😀x")
        assertEquals(0 to 5, buffer.cursor)
        assertEquals("Ａ😀x", buffer.rowText(0, 0, 5))
        assertEquals("😀", buffer.cellAt(0, 2).text)
        assertEquals(0, buffer.cellAt(0, 3).columns)
        buffer.send("\u001B[1;5H\u0301")
        assertEquals("😀\u0301", buffer.cellAt(0, 2).text)
    }

    @Test
    fun wideCharactersWrapBeforeTheLastColumnAndMarksDoNotWrap() {
        val buffer = TerminalBuffer(4, 3)
        buffer.send("abc界\u0301")
        assertEquals("abc ", buffer.rowText(0, 0, 4))
        assertEquals("界\u0301", buffer.cellAt(1, 0).text)
        assertEquals(1 to 2, buffer.cursor)
        buffer.send("界\u0308")
        assertEquals("界\u0308", buffer.cellAt(1, 2).text)
        assertEquals(1, buffer.cursorRow)
        buffer.send("x")
        assertEquals("x", buffer.cellAt(2, 0).text)
    }

    @Test
    fun disabledWrappingRejectsAWideCharacterThatCannotFit() {
        val buffer = TerminalBuffer(4, 3)
        buffer.send("\u001B[?7labc界x")
        assertEquals("abcx", buffer.rowText(0, 0, 4))
        assertEquals(0, buffer.cursorRow)
        val narrow = TerminalBuffer(1, 2)
        narrow.send("界")
        assertEquals("�", narrow.cellAt(0, 0).text)
    }

    @Test
    fun clearingAtAPendingWrapErasesTheWholeLastCharacter() {
        val buffer = TerminalBuffer(4, 3)
        buffer.send("ab界\u001B[K")
        assertEquals("ab  ", buffer.rowText(0, 0, 4))
        buffer.send("x")
        assertEquals("ab x", buffer.rowText(0, 0, 4))
        assertEquals(0, buffer.cursorRow)
        assertPairs(buffer)
    }

    @Test
    fun overwritingAndErasingEitherHalfClearsTheWholeOldCharacter() {
        for (col in 1..2) {
            val buffer = TerminalBuffer(8, 4)
            buffer.send("界z\u001B[1;${col}Hx")
            assertEquals(if (col == 1) "x z" else " xz", buffer.rowText(0, 0, 3))
            buffer.send("\u001B[1;1H界\u001B[1;${col}H\u001B[X")
            assertEquals("  z", buffer.rowText(0, 0, 3))
            assertPairs(buffer)
        }
    }

    @Test
    fun insertionDeletionAndShrinkingNeverLeaveAnOrphanHalf() {
        val buffer = TerminalBuffer(8, 4)
        buffer.send("a界b\u001B[1;2H\u001B[2P")
        assertEquals("ab", buffer.rowText(0, 0, 8).trimEnd())
        buffer.send("\u001B[2@界")
        assertEquals("a界b", buffer.rowText(0, 0, 8).trimEnd())
        buffer.send("\u001B[1;3H\u001B[@")
        assertPairs(buffer)
        buffer.send("\u001B[1;1Hab界")
        buffer.resize(3, 4)
        assertEquals("ab ", buffer.rowText(0, 0, 3))
        assertPairs(buffer)
    }

    @Test
    fun deletingAcrossTwoCharactersCannotJoinTheirUnrelatedHalves() {
        val buffer = TerminalBuffer(8, 4)
        buffer.send("界語z\u001B[1;2H\u001B[2P")
        assertEquals("  z", buffer.rowText(0, 0, 8).trimEnd())
        assertPairs(buffer)
    }

    @Test
    fun mixedOutputAndEditsKeepPairsValidThroughScreenChangesAndResize() {
        val buffer = TerminalBuffer(8, 4)
        val random = java.util.Random(1)
        val actions = listOf("界", "😀", "Ａ", "x", "\u0301", "\r\n",
            "\u001B[P", "\u001B[@", "\u001B[X", "\u001B[K", "\u001B[1K",
            "\u001B[?1049h", "\u001B[?1049l", "\u001B[?7l", "\u001B[?7h")
        repeat(1_000) {
            buffer.send(actions[random.nextInt(actions.size)])
            if (it % 17 == 0) buffer.resize(1 + random.nextInt(10), 1 + random.nextInt(6))
            if (it % 7 == 0) buffer.send("\u001B[${1 + random.nextInt(buffer.rows)};${1 + random.nextInt(buffer.cols)}H")
            assertPairs(buffer)
        }
    }

    private fun assertPairs(buffer: TerminalBuffer) {
        for (row in 0 until buffer.rows) for (col in 0 until buffer.cols) {
            val cell = buffer.cellAt(row, col)
            if (cell.columns == 0) {
                assertTrue(col > 0)
                assertEquals(2, buffer.cellAt(row, col - 1).columns)
                assertEquals("", cell.text)
            }
            if (cell.columns == 2) {
                assertTrue(col + 1 < buffer.cols)
                assertEquals(0, buffer.cellAt(row, col + 1).columns)
            }
        }
    }

    private fun TerminalBuffer.send(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        feed(bytes, bytes.size)
    }
}
