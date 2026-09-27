package uk.co.fireburn.lochssh.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalLinksTest {
    @Test
    fun findsUniqueWebLinksInSelectedOutput() {
        assertEquals(listOf("https://example.com/a?q=1", "http://localhost:8080/"),
            TerminalLinks.find("See https://example.com/a?q=1 and http://localhost:8080/. https://example.com/a?q=1"))
    }

    @Test
    fun removesSurroundingPunctuationButKeepsBalancedPaths() {
        assertEquals(listOf("https://example.com/a_(b)"),
            TerminalLinks.find("(https://example.com/a_(b))."))
    }

    @Test
    fun rejectsOtherSchemesCredentialsAndInvalidHosts() {
        assertTrue(TerminalLinks.find("file:///tmp/a javascript:alert(1) https://user@example.com/ https:///bad https://example.com:99999/").isEmpty())
    }
}
