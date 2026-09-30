package uk.co.fireburn.lochssh.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalBufferTest {

    private fun buffer(cols: Int = 43, rows: Int = 29) = TerminalBuffer(cols, rows)

    private fun TerminalBuffer.feed(text: String) {
        val bytes = text.toByteArray(Charsets.ISO_8859_1)
        feed(bytes, bytes.size)
    }

    private fun TerminalBuffer.line(row: Int) = rowText(row, 0, cols).trimEnd()

    private fun TerminalBuffer.screen() = (0 until rows).map { line(it) }

    @Test
    fun largeCursorCountsStayInsideTheGrid() {
        val buffer = buffer(cols = 8, rows = 4)
        buffer.feed("\u001B[2;2H\u001B[2147483647B\u001B[2147483647Cx")
        assertEquals('x', buffer.cellAt(3, 7).ch)
        buffer.feed("\u001B[2147483647A\u001B[2147483647Dy")
        assertEquals('y', buffer.cellAt(0, 0).ch)
        buffer.feed("\u001B[2147483647Ez\u001B[2147483647Fw")
        assertEquals('z', buffer.cellAt(3, 0).ch)
        assertEquals('w', buffer.cellAt(0, 0).ch)
    }

    @Test(timeout = 2_000)
    fun largeScrollCountsClearOnlyTheScrollRegion() {
        val buffer = buffer(cols = 8, rows = 4)
        buffer.feed("one\r\ntwo\r\nthree\r\nfour")
        buffer.feed("\u001B[2;3r\u001B[2147483647S")
        assertEquals(listOf("one", "", "", "four"), buffer.screen())
        buffer.feed("\u001B[2147483647T")
        assertEquals(listOf("one", "", "", "four"), buffer.screen())
        assertEquals(0, buffer.scrollbackSize)
    }

    @Test
    fun invalidColourComponentsCannotLeaveThePalette() {
        val buffer = buffer()
        buffer.feed("\u001B[38;5;-1ma\u001B[48;5;2147483647mb")
        assertEquals(TerminalBuffer.ANSI_COLORS[0], buffer.cellAt(0, 0).fg)
        assertEquals(0xFFEEEEEE.toInt(), buffer.cellAt(0, 1).bg)
        buffer.feed("\u001B[38;2;999;-1;256mc")
        assertEquals(0xFFFF00FF.toInt(), buffer.cellAt(0, 2).fg)
    }

    @Test
    fun oversizedEscapeSequenceIsDiscardedAndParsingResumes() {
        val buffer = buffer()
        buffer.feed("\u001B[" + "1;".repeat(10_000) + "Hok\u001B[2;1Hnext")
        assertEquals("ok", buffer.line(0))
        assertEquals("next", buffer.line(1))
    }

    @Test
    fun replacingALongerLineLeavesNothingBehind() {
        // What readline sends when a shorter command is recalled: backspaces to
        // the start of the input, delete the extra characters, then the new text.
        val buffer = buffer()
        buffer.feed("$ cd /tmp")
        buffer.feed("\b\b\b\b\b\b\b")
        buffer.feed("[4P")
        buffer.feed("pwd")

        assertEquals("$ pwd", buffer.line(0))
    }

    @Test
    fun recallingFromRealShellOutput() {
        val stream = javaClass.classLoader!!.getResourceAsStream("history_recall.bin")!!.readBytes()
        val buffer = buffer()
        buffer.feed(stream, stream.size)

        val screen = buffer.screen()
        assertTrue(
            "nothing should keep the tail of the replaced command: $screen",
            screen.none { it.contains("pwdtmp") }
        )
    }

    @Test
    fun aFullScreenProgramLeavesTheShellScreenBehindIt() {
        val stream = javaClass.classLoader!!.getResourceAsStream("less_session.bin")!!.readBytes()
        val buffer = buffer()
        buffer.feed(stream, stream.size)

        val screen = buffer.screen()
        // less swaps to its own screen and swaps back on quit, so what is left
        // is the shell, not the file it was showing.
        assertTrue(
            "the file contents should be gone: $screen",
            screen.none { it.contains("tcpmux") || it.contains("domain\t53") }
        )
        assertTrue(
            "the prompt should be back: $screen",
            screen.any { it.contains("fireburn@axion") }
        )
    }

    @Test
    fun alternateScreenIsRestoredOnExit() {
        val buffer = buffer()
        buffer.feed("shell line")
        buffer.feed("\u001B[?1049h")
        buffer.feed("\u001B[2J\u001B[1;1Hfull screen program")
        assertEquals("full screen program", buffer.line(0))

        buffer.feed("\u001B[?1049l")
        assertEquals("shell line", buffer.line(0))
    }

    @Test
    fun combinedPrivateModesSwitchScreensAndCursorVisibility() {
        val buffer = buffer(cols = 20, rows = 5)
        buffer.feed("shell")
        buffer.feed("\u001B[?25l")
        buffer.feed("\u001B[?25;1049h")
        assertEquals(true, buffer.cursorVisible)
        buffer.feed("full screen")
        assertEquals("full screen", buffer.line(0))

        buffer.feed("\u001B[?1049;25l")
        assertEquals(false, buffer.cursorVisible)
        assertEquals("shell", buffer.line(0))
    }

    @Test
    fun alternateScreenDoesNotReplaceTheShellSavedCursorOrScrollRegion() {
        val buffer = buffer(cols = 20, rows = 5)
        buffer.feed("\u001B[3;4H\u001B7\u001B[2;4r")
        buffer.feed("\u001B[?1049h\u001B[5;7H\u001B7\u001B[1;2r")
        buffer.feed("\u001B[?1049l")
        assertEquals(1 to 0, buffer.cursor)

        buffer.feed("\u001B8")
        assertEquals(2 to 3, buffer.cursor)
        buffer.feed("\u001B[4;1H\n")
        assertEquals(3 to 0, buffer.cursor)
    }

    @Test
    fun scrollRegionKeepsTheRestOfTheScreenStill() {
        val buffer = buffer(cols = 20, rows = 5)
        for (row in 1..5) {
            buffer.feed("\u001B[$row;1Hline$row")
        }
        // Scroll only rows 2 to 4.
        buffer.feed("\u001B[2;4r")
        buffer.feed("\u001B[4;1H")
        buffer.feed("\n")

        assertEquals("line1", buffer.line(0))
        assertEquals("line3", buffer.line(1))
        assertEquals("line4", buffer.line(2))
        assertEquals("", buffer.line(3))
        assertEquals("line5", buffer.line(4))
    }

    @Test
    fun deleteCharactersPullsTheRestOfTheLineLeft() {
        val buffer = buffer()
        buffer.feed("abcdef")
        buffer.feed("[1;1H")
        buffer.feed("[2P")

        assertEquals("cdef", buffer.line(0))
    }

    @Test
    fun insertCharactersPushesTheRestOfTheLineRight() {
        val buffer = buffer()
        buffer.feed("abc")
        buffer.feed("[1;1H")
        buffer.feed("[2@")

        assertEquals("  abc", buffer.line(0))
    }

    @Test
    fun eraseCharactersBlanksInPlace() {
        val buffer = buffer()
        buffer.feed("abcdef")
        buffer.feed("[1;2H")
        buffer.feed("[3X")

        assertEquals("a   ef", buffer.rowText(0, 0, 6))
    }

    @Test
    fun normalScreenKeepsScrolledLinesForReview() {
        val buffer = buffer(cols = 8, rows = 2)
        buffer.feed("one\r\ntwo\r\nthree")

        assertEquals(1, buffer.scrollbackSize)
        assertEquals("one", buffer.visibleRowText(0, 0, 8, 1).trimEnd())
        assertEquals("two", buffer.visibleRowText(1, 0, 8, 1).trimEnd())
        assertEquals("three", buffer.line(1))
        buffer.resize(10, 2)
        assertEquals("one", buffer.visibleRowText(0, 0, 10, 1).trimEnd())
    }

    @Test
    fun shrinkingAndGrowingKeepsThePromptAndEarlierRows() {
        val buffer = buffer(cols = 8, rows = 4)
        buffer.feed("one\r\ntwo\r\nthree\r\nfour")

        buffer.resize(8, 2)
        assertEquals("three", buffer.line(0))
        assertEquals("four", buffer.line(1))
        assertEquals(2, buffer.scrollbackSize)
        assertEquals(1 to 4, buffer.cursor)

        buffer.resize(8, 4)
        assertEquals(listOf("one", "two", "three", "four"), buffer.screen())
        assertEquals(0, buffer.scrollbackSize)
        assertEquals(3 to 4, buffer.cursor)
    }

    @Test
    fun alternateAndPartialScreensDoNotEnterScrollback() {
        val buffer = buffer(cols = 8, rows = 3)
        buffer.feed("shell")
        buffer.feed("\u001B[?1049h")
        buffer.feed("one\r\ntwo\r\nthree\r\nfour")
        buffer.feed("\u001B[?1049l")
        assertEquals(0, buffer.scrollbackSize)

        buffer.feed("\u001B[2;3r\u001B[3;1H\n")
        assertEquals(0, buffer.scrollbackSize)
    }

    @Test
    fun clearSavedLinesLeavesTheCurrentScreen() {
        val buffer = buffer(cols = 8, rows = 2)
        buffer.feed("one\r\ntwo\r\nthree")
        buffer.feed("\u001B[3J")

        assertEquals(0, buffer.scrollbackSize)
        assertEquals("two", buffer.line(0))
        assertEquals("three", buffer.line(1))
    }

    @Test
    fun selectedTextUsesTheVisibleScrollbackRows() {
        val buffer = buffer(cols = 8, rows = 2)
        buffer.feed("one\r\ntwo\r\nthree")

        val first = TerminalBuffer.Point(0, 1)
        val last = TerminalBuffer.Point(1, 2)
        assertEquals("ne\ntwo", buffer.selectedText(first, last, 1))
        assertEquals("ne\ntwo", buffer.selectedText(last, first, 1))
    }

    @Test
    fun selectionMadeBeforeResizeStaysInsideTheNewGrid() {
        val buffer = buffer(cols = 8, rows = 4)
        buffer.feed("one\r\ntwo\r\nthree\r\nfour")
        buffer.resize(4, 2)

        assertEquals("thre\nfour", buffer.selectedText(
            TerminalBuffer.Point(0, 0), TerminalBuffer.Point(3, 7), 0
        ))
    }

    @Test
    fun repeatedResizesKeepVisibleRowsInBounds() {
        val buffer = buffer(cols = 80, rows = 24)
        repeat(80) { buffer.feed("line $it\r\n") }
        repeat(100) { step ->
            val cols = 20 + (step * 17) % 101
            val rows = 5 + (step * 11) % 38
            buffer.resize(cols, rows)
            for (row in 0 until rows) {
                assertEquals(cols, buffer.visibleRowText(row, 0, cols, buffer.scrollbackSize).length)
            }
            buffer.selectedText(TerminalBuffer.Point(23, 79), TerminalBuffer.Point(0, 0), 0)
        }
    }

    @Test
    fun bracketedPasteModeFollowsRemoteRequests() {
        val buffer = buffer()
        buffer.feed("\u001B[?2004h")
        assertEquals(true, buffer.bracketedPaste)
        buffer.feed("\u001B[?2004l")
        assertEquals(false, buffer.bracketedPaste)
    }
}
