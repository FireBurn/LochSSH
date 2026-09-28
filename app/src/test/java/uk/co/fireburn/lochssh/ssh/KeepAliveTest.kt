package uk.co.fireburn.lochssh.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeepAliveTest {
    @Test
    fun acceptsDisabledAndLargestSafeIntervalInTheEditor() {
        assertEquals(0, KeepAlive.parse("0"))
        assertEquals(1, KeepAlive.parse("1"))
        assertEquals(KeepAlive.MAX_SECONDS, KeepAlive.parse(KeepAlive.MAX_SECONDS.toString()))
    }

    @Test
    fun rejectsInvalidAndOverflowingEditorValues() {
        listOf("", "-1", "1.5", " 30 ", "never", Int.MAX_VALUE.toString(),
            (KeepAlive.MAX_SECONDS + 1).toString(), "99999999999999999999").forEach {
            assertNull(it, KeepAlive.parse(it))
        }
    }

    @Test
    fun preservesDisabledAndShortIntervals() {
        assertEquals(0, KeepAlive.milliseconds(0))
        assertEquals(1000, KeepAlive.milliseconds(1))
        assertEquals(30000, KeepAlive.milliseconds(30))
        assertEquals(2147483000, KeepAlive.milliseconds(KeepAlive.MAX_SECONDS))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOverflow() { KeepAlive.milliseconds(KeepAlive.MAX_SECONDS + 1) }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeIntervals() { KeepAlive.milliseconds(-1) }
}
