package uk.co.fireburn.lochssh.ssh

import org.junit.Assert.assertEquals
import org.junit.Test

class KeepAliveTest {
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
