package uk.co.fireburn.lochssh.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.fireburn.lochssh.data.db.RemoteSessionModes

class AutoReattachTest {
    @Test
    fun appliesToAttachedSessionsAndAutomaticModes() {
        assertTrue(AutoReattach.applies("tmux attach-session -t '\$0'", RemoteSessionModes.SHELL))
        assertTrue(AutoReattach.applies("screen -A -r '1.work'", RemoteSessionModes.SHELL))
        assertTrue(AutoReattach.applies(null, RemoteSessionModes.TMUX))
        assertTrue(AutoReattach.applies(null, RemoteSessionModes.SCREEN))
        assertFalse(AutoReattach.applies(null, RemoteSessionModes.SHELL))
        assertFalse(AutoReattach.applies("ls", RemoteSessionModes.ASK))
    }

    @Test
    fun onlyDropsRetryNotCleanExits() {
        assertFalse(AutoReattach.shouldRetry(0))
        assertTrue(AutoReattach.shouldRetry(-1))
        assertTrue(AutoReattach.shouldRetry(255))
    }

    @Test
    fun waitsGrowAndStopAfterTheLimit() {
        assertEquals(2_000L, AutoReattach.delayMs(0))
        assertEquals(30_000L, AutoReattach.delayMs(AutoReattach.MAX_TRIES - 1))
        assertNull(AutoReattach.delayMs(AutoReattach.MAX_TRIES))
    }
}
