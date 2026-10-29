package uk.co.fireburn.lochssh.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class LinkCandidatesTest {
    private val url = "https://claude.ai/oauth/authorize?code=true&client_id=abc&redirect_uri=x"

    @Test
    fun aLinkWithNoDoubtHasOneCandidate() {
        val text = "open $url now"
        assertEquals(listOf(url), TerminalLinks.candidatesAt(text, text.indexOf("oauth")))
        assertEquals(emptyList<String>(), TerminalLinks.candidatesAt(text, 1))
    }

    @Test
    fun aPossibleLineBreakGivesTheLongAndTheShortReading() {
        val cut = 30
        val text = url
        val found = TerminalLinks.candidatesAt(text, 5, listOf(cut))
        assertEquals(listOf(url, url.substring(0, cut)), found)
    }

    @Test
    fun severalPossibleBreaksGiveEachCutLongestFirst() {
        val found = TerminalLinks.candidatesAt(url, 5, listOf(25, 45))
        assertEquals(listOf(url, url.substring(0, 45), url.substring(0, 25)), found)
    }

    @Test
    fun aBreakTooCloseToTheStartOrOutsideTheLinkIsIgnored() {
        val text = "see $url"
        assertEquals(listOf(url), TerminalLinks.candidatesAt(text, 10, listOf(5, 6, text.length + 5)))
    }

    @Test
    fun selectedTextWithALineBreakGivesBothReadings() {
        val selected = "https://example.com/aaa\nbbb"
        assertEquals(listOf("https://example.com/aaabbb", "https://example.com/aaa"), TerminalLinks.candidatesIn(selected))
        assertEquals(listOf("https://example.com/x"), TerminalLinks.candidatesIn("go to https://example.com/x."))
    }

    @Test
    fun pastedTextLosesControlCharactersButKeepsLinesAndTabs() {
        assertEquals("ls\n\tcd x", PasteText.clean("ls\r\n\tcd x"))
        assertEquals("ab", PasteText.clean("a\u001Bb\u0007"))
        assertEquals("a[201~b", PasteText.clean("a\u001B[201~b"))
        assertEquals(3, PasteText.lineCount("a\nb\nc"))
    }

    @Test
    fun theLongPastePreviewShowsTheStartAndHowMuchMore() {
        val text = (1..12).joinToString("\n") { "line $it" }
        val shown = PasteText.preview(text)
        assertEquals("line 1", shown.lines().first())
        assertEquals("… and 4 more", shown.lines().last())
        assertEquals("x".repeat(80) + "…", PasteText.preview("x".repeat(100)))
    }
}
