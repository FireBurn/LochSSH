package uk.co.fireburn.lochssh.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoReattachTest {
    @Test
    fun appliesToEveryConnectionExceptTheLocalShell() {
        assertTrue(AutoReattach.applies(false))
        assertFalse(AutoReattach.applies(true))
    }

    @Test
    fun onlyDropsRetryNotCleanExits() {
        assertFalse(AutoReattach.shouldRetry(0))
        assertTrue(AutoReattach.shouldRetry(-1))
        assertTrue(AutoReattach.shouldRetry(255))
    }

    @Test
    fun waitsGrowAndStopAfterTheLimit() {
        assertEquals(500L, AutoReattach.delayMs(0))
        assertEquals(30_000L, AutoReattach.delayMs(AutoReattach.MAX_TRIES - 1))
        assertNull(AutoReattach.delayMs(AutoReattach.MAX_TRIES))
    }
}
