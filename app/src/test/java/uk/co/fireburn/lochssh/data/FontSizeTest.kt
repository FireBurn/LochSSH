package uk.co.fireburn.lochssh.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FontSizeTest {
    @Test
    fun smallSizesMoveByOneAndStopAtTheLimits() {
        assertEquals(13, AppPreferences.nextFontSize(14, -1))
        assertEquals(3, AppPreferences.nextFontSize(4, -1))
        assertEquals(3, AppPreferences.nextFontSize(3, -1))
        assertEquals(15, AppPreferences.nextFontSize(14, 1))
    }

    @Test
    fun largeSizesMoveByTwoAndStopAtTheLimits() {
        assertEquals(26, AppPreferences.nextFontSize(24, 1))
        assertEquals(24, AppPreferences.nextFontSize(26, -1))
        assertEquals(23, AppPreferences.nextFontSize(24, -1))
        assertEquals(64, AppPreferences.nextFontSize(63, 1))
        assertEquals(64, AppPreferences.nextFontSize(64, 1))
    }

    @Test
    fun theRangeIsMuchWiderThanBefore() {
        assertEquals(3, AppPreferences.MIN_FONT_SIZE)
        assertEquals(64, AppPreferences.MAX_FONT_SIZE)
    }
}
