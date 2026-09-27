package uk.co.fireburn.lochssh.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalThemeTest {
    @Test
    fun resolvesDefaultsAndPreservesExplicitColors() {
        TerminalTheme.entries.forEach { theme ->
            assertEquals(theme.foreground, theme.color(TerminalBuffer.DEFAULT_FG))
            assertEquals(theme.background, theme.color(TerminalBuffer.DEFAULT_BG))
            assertEquals(0xFFE0E1DD.toInt(), theme.color(0xFFE0E1DD.toInt()))
        }
    }

    @Test
    fun keepsInverseDefaultsReadable() {
        val buffer = TerminalBuffer(20, 5)
        val bytes = "\u001b[7mX".toByteArray()
        buffer.feed(bytes, bytes.size)
        val cell = buffer.cellAt(0, 0)
        assertEquals(TerminalTheme.PAPER.background, TerminalTheme.PAPER.color(cell.fg))
        assertEquals(TerminalTheme.PAPER.foreground, TerminalTheme.PAPER.color(cell.bg))
    }

    @Test
    fun fallsBackForMissingOrUnknownSettings() {
        assertEquals(TerminalTheme.LOCH, TerminalTheme.decode(null))
        assertEquals(TerminalTheme.LOCH, TerminalTheme.decode("unknown"))
        assertEquals(TerminalTheme.PAPER, TerminalTheme.decode("PAPER"))
    }
}
