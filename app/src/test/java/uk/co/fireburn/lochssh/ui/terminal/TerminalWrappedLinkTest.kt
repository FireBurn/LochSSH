package uk.co.fireburn.lochssh.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TerminalWrappedLinkTest {
    @Test fun wideCharacterWrapDoesNotAddPaddingToTheUrl() {
        val url = "https://a.test/1234😀end"
        val buffer = buffer(url)
        assertEquals(url, linkAt(buffer, TerminalBuffer.Point(0, 3), 0))
        assertEquals(url, linkAt(buffer, TerminalBuffer.Point(1, 2), 0))
        assertEquals(url, buffer.selectedText(TerminalBuffer.Point(0, 0), TerminalBuffer.Point(1, 4), 0))
    }

    @Test fun insertingOrDeletingARowBreaksItsOldWrapBoundary() {
        val prefix = "https://a.test/12345"
        for (command in listOf("L", "M")) {
            val buffer = buffer(prefix + "tail\r\nunrelated", cols = prefix.length)
            val edit = "\u001B[2;1H\u001B[$command".toByteArray()
            buffer.feed(edit, edit.size)
            assertEquals(prefix, linkAt(buffer, TerminalBuffer.Point(0, 3), 0))
        }
    }
    @Test fun exactFitLinkDoesNotAbsorbTextAfterAnExplicitNewline() {
        val url = "https://a.test/12345"
        val buffer = buffer("$url\r\nunrelated", cols = url.length)
        assertEquals(url, linkAt(buffer, TerminalBuffer.Point(0, 4), 0))
        assertNull(linkAt(buffer, TerminalBuffer.Point(1, 3), 0))
    }

    @Test fun selectionJoinsWrappedLinksButKeepsExplicitNewlines() {
        val buffer = buffer(url)
        val selected = buffer.selectedText(TerminalBuffer.Point(0, 0),
            TerminalBuffer.Point((url.length - 1) / 20, (url.length - 1) % 20), 0)
        assertEquals(url, selected)
        assertEquals(listOf(url), TerminalLinks.find(selected))
        val separate = buffer("one\r\ntwo")
        assertEquals("one\ntwo", separate.selectedText(TerminalBuffer.Point(0, 0), TerminalBuffer.Point(1, 2), 0))
    }

    @Test fun wrapsRemainVisibleInHistoryAndAfterGrowingTheWidth() {
        val url = "https://a.test/" + "x".repeat(30)
        val buffer = TerminalBuffer(20, 3)
        val bytes = "$url\r\nnext".toByteArray()
        buffer.feed(bytes, bytes.size)
        assertEquals(url, linkAt(buffer, TerminalBuffer.Point(0, 4), 1))
        buffer.resize(40, 3)
        assertEquals(url, linkAt(buffer, TerminalBuffer.Point(0, 4), 1))
    }

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
