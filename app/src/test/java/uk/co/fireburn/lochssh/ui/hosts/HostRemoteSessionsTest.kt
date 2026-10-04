package uk.co.fireburn.lochssh.ui.hosts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.fireburn.lochssh.ssh.SessionRegistry
import uk.co.fireburn.lochssh.ssh.SavedHostKey

class HostRemoteSessionsTest {
    private fun scan(id: Long = 1, host: Long = 7, error: String? = null) =
        SessionRegistry.Session(id, host, "Host", error = error, findRemoteSessions = true)

    @Test fun startsANewScanWithoutChangingOtherHosts() {
        assertEquals(4L, HostRemoteSessions.sessionId(7, listOf(scan(host = 8))) { 4 })
    }

    @Test fun preventsRepeatedTapsWhileFinding() {
        assertNull(HostRemoteSessions.sessionId(7, listOf(scan())) { error("Unexpected new session") })
    }

    @Test fun reusesADroppedScan() {
        assertEquals(1L, HostRemoteSessions.sessionId(7, listOf(scan(error = "Dropped"))) { error("Unexpected new session") })
    }

    @Test fun normalOpeningShellsDoNotSuppressDiscovery() {
        assertEquals(4L, HostRemoteSessions.sessionId(7, listOf(scan().copy(findRemoteSessions = false))) { 4 })
    }

    @Test fun keepsExistingServerKeyReviews() {
        val review = scan(error = "Check key").copy(findRemoteSessions = false,
            hostKeyReview = SavedHostKey("host", "type", "fingerprint", "key"))
        assertNull(HostRemoteSessions.sessionId(7, listOf(review)) { error("Unexpected new session") })
    }

    @Test fun keepsDiscoveryOnRetryAndServiceFailure() {
        val registry = SessionRegistry()
        registry.opening(1, 7, "Host", findRemoteSessions = true)
        assertEquals("Finding remote sessions", registry.find(1)?.statusText)
        registry.failed(1, "Dropped")
        registry.opening(1, 7, "Host")
        registry.closeSessions(setOf(1), "Service stopped")
        assertTrue(requireNotNull(registry.find(1)).findRemoteSessions)
    }
}
