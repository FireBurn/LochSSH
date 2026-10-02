package uk.co.fireburn.lochssh.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRegistryTest {
    @Test
    fun retryKeepsTheRemoteSessionTarget() {
        val registry = SessionRegistry()
        val id = registry.newId()
        val command = "tmux attach-session -t '\$1'"
        registry.opening(id, 1, "host", command)
        registry.failed(id, "Connection lost")
        assertEquals(command, registry.find(id)?.remoteCommand)
        registry.opening(id, 1, "host")
        assertEquals(command, registry.find(id)?.remoteCommand)
        registry.closeSessions(setOf(id), "Service stopped")
        assertEquals(command, registry.find(id)?.remoteCommand)
    }

    @Test
    fun removesOpeningSessionsWhenTheServiceStops() {
        val registry = SessionRegistry()
        registry.opening(1, 10, "First")
        registry.opening(2, 20, "Second")
        registry.opening(3, 30, "Already failed")
        registry.failed(3, "Connection refused")

        registry.closeSessions(setOf(1, 2))

        assertNull(registry.find(1))
        assertNull(registry.find(2))
        assertEquals("Connection refused", registry.find(3)?.error)
    }

    @Test
    fun keepsTimedOutSessionsWithAReasonAndNoPendingKeyReview() {
        val registry = SessionRegistry()
        registry.opening(1, 10, "First")
        registry.failed(1, "Key not approved", SavedHostKey("test", "type", "fingerprint", "key"))
        registry.closeSessions(setOf(1), "Android stopped background connections")

        val session = requireNotNull(registry.find(1))
        assertEquals("First", session.label)
        assertEquals("Android stopped background connections", session.statusText)
        assertEquals("View", session.actionLabel)
        assertNull(session.hostKeyReview)
        assertFalse(session.isConnected)
    }

    @Test
    fun keepsRecentForwardWarningsUntilDismissed() {
        val registry = SessionRegistry()
        val id = registry.newId()
        registry.opening(id, 1, "Host")

        registry.addForwardWarning(id, "same error")
        registry.addForwardWarning(id, "same error")
        assertEquals(listOf("same error"), registry.find(id)?.forwardWarnings)

        (1..6).forEach { registry.addForwardWarning(id, "error $it") }
        assertEquals((2..6).map { "error $it" }, registry.find(id)?.forwardWarnings)

        registry.dismissForwardWarnings(id)
        assertTrue(registry.find(id)?.forwardWarnings.isNullOrEmpty())
    }

    @Test
    fun distinguishesOpeningAndFailure() {
        val registry = SessionRegistry()
        registry.opening(1, 2, "Test")
        assertEquals("Connecting", registry.find(1)?.statusText)
        assertEquals("View", registry.find(1)?.actionLabel)
        assertFalse(requireNotNull(registry.find(1)).isConnected)
        registry.failed(1, "Connection refused")
        assertEquals("Connection refused", registry.find(1)?.statusText)
        assertEquals("View", registry.find(1)?.actionLabel)
    }

    @Test
    fun showsKeyReviewAndClearsItOnRetry() {
        val registry = SessionRegistry()
        val key = SavedHostKey("jump.test", "ssh-ed25519", "fingerprint", "key")
        registry.opening(1, 2, "Destination")
        registry.failed(1, "Key not approved", key)
        assertEquals("Server key approval needed", registry.find(1)?.statusText)
        assertEquals("Review", registry.find(1)?.actionLabel)
        registry.opening(1, 2, "Destination")
        assertNull(registry.find(1)?.hostKeyReview)
        assertNull(registry.find(1)?.error)
        assertEquals("Connecting", registry.find(1)?.statusText)
    }

    @Test
    fun dismissesReviewWithoutClaimingConnectionOrDiscardingFailure() {
        val registry = SessionRegistry()
        registry.opening(1, 2, "Test")
        registry.failed(1, "Key not approved", SavedHostKey("test", "type", "fingerprint", "key"))
        registry.dismissHostKeyReview(1)
        assertEquals("Key not approved", registry.find(1)?.statusText)
        assertFalse(requireNotNull(registry.find(1)).isConnected)
    }
}
