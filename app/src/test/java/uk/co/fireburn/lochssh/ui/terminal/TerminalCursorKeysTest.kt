package uk.co.fireburn.lochssh.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalCursorKeysTest {
    @Test
    fun cursorKeysFollowTheProgramsModeChanges() {
        val terminal = TerminalSession(80, 24)
        assertEquals("\u001B[A", encode(terminal, TerminalKey.Arrow('A')))
        terminal.send("\u001B[?1h")
        for (final in "ABCD") {
            assertEquals("\u001BO$final", encode(terminal, TerminalKey.Arrow(final)))
        }
        assertEquals("\u001BOH", encode(terminal, TerminalKey.Home))
        assertEquals("\u001BOF", encode(terminal, TerminalKey.End))

        terminal.send("\u001B[?1l")
        assertEquals("\u001B[A", encode(terminal, TerminalKey.Arrow('A')))
        assertEquals("\u001B[H", encode(terminal, TerminalKey.Home))
        assertEquals("\u001B[F", encode(terminal, TerminalKey.End))
    }

    @Test
    fun modifiersAndPageKeysKeepTheirOwnSequencesInApplicationMode() {
        val terminal = TerminalSession(80, 24)
        terminal.send("\u001B[?1h")
        assertEquals("\u001B[1;5A", encode(terminal, TerminalKey.Arrow('A'), ctrl = true))
        assertEquals("\u001B[1;3H", encode(terminal, TerminalKey.Home, alt = true))
        assertEquals("\u001B[1;7F", encode(terminal, TerminalKey.End, ctrl = true, alt = true))
        assertEquals("\u001B[5~", encode(terminal, TerminalKey.PgUp))
        assertEquals("\u001B[6;3~", encode(terminal, TerminalKey.PgDn, alt = true))
        assertEquals("\u001B[3;5~", encode(terminal, TerminalKey.Del, ctrl = true))
    }

    @Test
    fun cursorModeStaysWithItsSessionThroughResize() {
        val first = TerminalSession(80, 24)
        val second = TerminalSession(80, 24)
        first.send("\u001B[?1;1049h")
        first.buffer.resize(43, 10)

        assertTrue(first.buffer.applicationCursor)
        assertFalse(second.buffer.applicationCursor)
        assertEquals("\u001BOB", encode(first, TerminalKey.Arrow('B')))
        assertEquals("\u001B[B", encode(second, TerminalKey.Arrow('B')))
        first.send("\u001B[?1049;1l")
        assertFalse(first.buffer.applicationCursor)
    }

    private fun encode(terminal: TerminalSession, key: TerminalKey,
        ctrl: Boolean = false, alt: Boolean = false): String =
        requireNotNull(TerminalKeyEncoder.encode(key, ctrl, alt, false,
            terminal.buffer.read { terminal.buffer.applicationCursor })).toString(Charsets.US_ASCII)

    private fun TerminalSession.send(text: String) {
        val bytes = text.toByteArray(Charsets.US_ASCII)
        feed(bytes, bytes.size)
    }
}
