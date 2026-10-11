package uk.co.fireburn.lochssh.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TerminalWrappedLinkTest {
    private val url = "https://claude.ai/oauth/authorize?code=true&client_id=abc1234"

    private fun buffer(text: String, cols: Int = 20): TerminalBuffer {
        val buffer = TerminalBuffer(cols, 8)
        val bytes = text.toByteArray()
        buffer.feed(bytes, bytes.size)
        return buffer
    }

    @Test
    fun aLinkWrappedOverSeveralRowsIsFoundFromAnyOfThem() {
        val buffer = buffer("open this:\r\n$url\r\nthen paste the code")
        for (row in 1..4) {
            val col = if (row == 4) 0 else 3
            assertEquals(url, linkAt(buffer, TerminalBuffer.Point(row, col), 0))
        }
    }

    @Test
    fun aLinkThatFitsOnOneRowStillWorks() {
        val buffer = buffer("see https://a.test/x now", cols = 40)
        assertEquals("https://a.test/x", linkAt(buffer, TerminalBuffer.Point(0, 8), 0))
        assertNull(linkAt(buffer, TerminalBuffer.Point(0, 1), 0))
        assertNull(linkAt(buffer, TerminalBuffer.Point(0, 30), 0))
    }

    @Test
    fun textOnTheRowAfterAFullRowThatIsNotALinkIsNotJoinedIntoOne() {
        val buffer = buffer("aaaaaaaaaaaaaaaaaaaa\r\nhttps://a.test/x")
        assertEquals("https://a.test/x", linkAt(buffer, TerminalBuffer.Point(1, 3), 0))
    }
}
