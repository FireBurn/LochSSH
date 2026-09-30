package uk.co.fireburn.lochssh.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalTypefaceTest {
    @Test
    fun retainsKnownChoices() {
        TerminalTypeface.entries.forEach { assertEquals(it, TerminalTypeface.decode(it.name)) }
    }

    @Test
    fun oldAndUnknownSettingsKeepTheOriginalFont() {
        assertEquals(TerminalTypeface.NOTO, TerminalTypeface.decode(null))
        assertEquals(TerminalTypeface.NOTO, TerminalTypeface.decode(""))
        assertEquals(TerminalTypeface.NOTO, TerminalTypeface.decode("missing"))
    }
}
