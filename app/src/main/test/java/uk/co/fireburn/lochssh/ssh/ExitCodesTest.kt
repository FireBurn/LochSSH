package uk.co.fireburn.lochssh.ssh

import org.junit.Assert.assertEquals
import org.junit.Test

class ExitCodesTest {
    @Test
    fun aFailedReadIsAlwaysALostConnection() {
        assertEquals(AutoReattach.LOST, ExitCodes.of(true, true, 0, true))
        assertEquals(AutoReattach.LOST, ExitCodes.of(true, false, -1, false))
    }

    @Test
    fun aKnownExitStatusIsPassedOn() {
        assertEquals(0, ExitCodes.of(false, true, 0, true))
        assertEquals(1, ExitCodes.of(false, true, 1, true))
        assertEquals(130, ExitCodes.of(false, true, 130, false))
    }

    @Test
    fun aClosedChannelOnALiveLinkBeforeTheStatusArrivesIsAnExit() {
        assertEquals(0, ExitCodes.of(false, true, -1, true))
    }

    @Test
    fun aClosedChannelOnADeadLinkIsALostConnection() {
        assertEquals(AutoReattach.LOST, ExitCodes.of(false, true, -1, false))
        assertEquals(AutoReattach.LOST, ExitCodes.of(false, false, -1, true))
    }
}
