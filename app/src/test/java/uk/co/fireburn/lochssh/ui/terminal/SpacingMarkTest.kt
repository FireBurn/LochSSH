package uk.co.fireburn.lochssh.ui.terminal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpacingMarkTest {
    @Test
    fun recognisesVowelSignsThatAreDrawnWithTheirLetter() {
        assertTrue(isSpacingMark("\u093E"))
        assertTrue(isSpacingMark("\u093F"))
        assertTrue(isSpacingMark("\u09BE"))
        assertTrue(isSpacingMark("\u0BBF"))
    }

    @Test
    fun ignoresLettersMarksThatJoinAlreadyAndEmptyCells() {
        assertFalse(isSpacingMark("a"))
        assertFalse(isSpacingMark("\u0915"))
        assertFalse(isSpacingMark("\u0301"))
        assertFalse(isSpacingMark("\u094D"))
        assertFalse(isSpacingMark(""))
        assertFalse(isSpacingMark("\u093E\u093E"))
    }
}
