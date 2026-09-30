package uk.co.fireburn.lochssh.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalRepliesTest {
    @Test
    fun reportsStatusAndCursorWithoutPrintingTheReply() {
        val replies = mutableListOf<String>()
        val terminal = TerminalSession(8, 4, replies::add)
        terminal.send("\u001B[3;4H\u001B[5n\u001B[6n\u001B[?6n")

        assertEquals(listOf("\u001B[0n", "\u001B[3;4R", "\u001B[?3;4R"), replies)
        assertEquals(2 to 3, terminal.buffer.cursor)
        assertTrue(terminal.buffer.transcript().isBlank())
    }

    @Test
    fun queriesSurviveSplitReadsAndReportTheLastColumnBeforeWrapping() {
        val replies = mutableListOf<String>()
        val terminal = TerminalSession(8, 4, replies::add)
        terminal.send("abcdefgh\u001B[")
        terminal.send("6")
        terminal.send("n")

        assertEquals(listOf("\u001B[1;8R"), replies)
        terminal.send("i\u001B[6n")
        assertEquals("\u001B[2;2R", replies.last())
    }

    @Test
    fun reportsTheCurrentGridSizeAfterResize() {
        val replies = mutableListOf<String>()
        val terminal = TerminalSession(80, 24, replies::add)
        terminal.buffer.resize(43, 10)
        terminal.send("\u001B[18t")

        assertEquals(listOf("\u001B[8;10;43t"), replies)
    }

    @Test
    fun unsupportedQueriesDoNotInventReplies() {
        val replies = mutableListOf<String>()
        val terminal = TerminalSession(8, 4, replies::add)
        terminal.send("\u001B[?5n\u001B[15n\u001B[5;6n\u001B[18;1t\u001B[?18t")

        assertTrue(replies.isEmpty())
    }

    private fun TerminalSession.send(text: String) {
        val bytes = text.toByteArray(Charsets.US_ASCII)
        feed(bytes, bytes.size)
    }
}
