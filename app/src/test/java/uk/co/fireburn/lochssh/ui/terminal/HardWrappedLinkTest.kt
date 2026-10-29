package uk.co.fireburn.lochssh.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class HardWrappedLinkTest {
    private val url = "https://claude.ai/oauth/authorize?code=true&client_id=abcdef"

    // What tmux and full-screen programs do: break the line themselves, so nothing records a wrap.
    private fun hardWrapped(text: String, width: Int): TerminalBuffer {
        val buffer = TerminalBuffer(width, 8)
        val bytes = text.chunked(width).joinToString("\r\n").toByteArray()
        buffer.feed(bytes, bytes.size)
        return buffer
    }

    @Test
    fun aLineABreakProgramSplitGivesTheJoinedAddressFirstAndTheCutOnesAfter() {
        val buffer = hardWrapped(url, 20)
        val found = linksAt(buffer, TerminalBuffer.Point(1, 4), 0)
        assertEquals(listOf(url, url.substring(0, 40), url.substring(0, 20)), found)
    }

    @Test
    fun theSameAddressIsFoundFromAnyRowOfIt() {
        val buffer = hardWrapped(url, 20)
        for (row in 0..2) assertEquals(url, linksAt(buffer, TerminalBuffer.Point(row, 2), 0).first())
    }

    @Test
    fun theSingleBestReadingIsOnlyWhatIsCertain() {
        val buffer = hardWrapped(url, 20)
        assertEquals(url.substring(0, 20), linkAt(buffer, TerminalBuffer.Point(0, 2), 0))
    }

    @Test
    fun aShortRowEndsTheAddressWithoutAskingAnything() {
        val buffer = TerminalBuffer(40, 4)
        val bytes = "go to https://a.test/page\r\nnext line".toByteArray()
        buffer.feed(bytes, bytes.size)
        assertEquals(listOf("https://a.test/page"), linksAt(buffer, TerminalBuffer.Point(0, 10), 0))
    }
}
