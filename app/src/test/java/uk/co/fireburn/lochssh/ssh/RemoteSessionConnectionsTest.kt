package uk.co.fireburn.lochssh.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.fireburn.lochssh.data.db.RemoteSessionModes

class RemoteSessionConnectionsTest {
    private val entry = RemoteSessionEntry(RemoteSessionModes.SCREEN, "123.build", "build", false)

    @Test
    fun reusesFailedSessionDespiteChangedAttachmentState() {
        val existing = SessionRegistry.Session(7, 1, "host", error = "Disconnected",
            remoteCommand = RemoteSessions.attach(entry.copy(attached = true)))
        val plan = RemoteSessionConnections.plan(1, listOf(entry, entry), listOf(existing)) {
            error("Must reuse the dropped session")
        }
        assertEquals(1, plan.size)
        assertEquals(7L, plan.single().id)
        assertTrue(plan.single().connect)
        assertEquals(RemoteSessions.attach(entry), plan.single().command)
    }

    @Test
    fun doesNotRestartAnOpeningSession() {
        val existing = SessionRegistry.Session(7, 1, "host", remoteCommand = RemoteSessions.attach(entry))
        val plan = RemoteSessionConnections.plan(1, listOf(entry), listOf(existing)) { error("Duplicate") }
        assertFalse(plan.single().connect)
    }

    @Test
    fun keepsServerKeyReviewInsteadOfStartingAnotherConnection() {
        val existing = SessionRegistry.Session(7, 1, "host", error = "Key approval needed",
            hostKeyReview = SavedHostKey("host", "type", "fingerprint", "key"),
            remoteCommand = RemoteSessions.attach(entry))
        val plan = RemoteSessionConnections.plan(1, listOf(entry), listOf(existing)) { error("Duplicate") }
        assertEquals(7L, plan.single().id)
        assertFalse(plan.single().connect)
    }

    @Test
    fun prefersAnOpeningConnectionOverAnOlderFailedRow() {
        val failed = SessionRegistry.Session(7, 1, "host", error = "Disconnected",
            remoteCommand = RemoteSessions.attach(entry))
        val opening = failed.copy(id = 8, error = null)
        val plan = RemoteSessionConnections.plan(1, listOf(entry), listOf(failed, opening)) { error("Duplicate") }
        assertEquals(8L, plan.single().id)
        assertFalse(plan.single().connect)
    }

    @Test
    fun createsAConnectionWhenTheMatchingSessionIsOnAnotherHost() {
        val existing = SessionRegistry.Session(7, 2, "other", remoteCommand = RemoteSessions.attach(entry))
        val plan = RemoteSessionConnections.plan(1, listOf(entry), listOf(existing)) { 8L }
        assertEquals(8L, plan.single().id)
        assertTrue(plan.single().connect)
    }

    @Test
    fun aTmuxSessionOpenedByNameIsNotListedAgainWhenAScanFindsItById() {
        val scanned = RemoteSessions.parseTmux("\$5|loch42\tbash\tbash|1").single()
        assertEquals("loch42", scanned.sessionName)
        val existing = SessionRegistry.Session(9, 1, "host",
            remoteCommand = RemoteSessions.create(RemoteSessionModes.TMUX, "loch42"))
        val plan = RemoteSessionConnections.plan(1, listOf(scanned), listOf(existing)) { error("Duplicate") }
        assertEquals(9L, plan.single().id)
        assertFalse(plan.single().connect)
    }

    @Test
    fun theAutomaticTmuxSessionIsRecognisedByItsName() {
        val scanned = RemoteSessionEntry(RemoteSessionModes.TMUX, "\$1", "lochssh · bash", false, "lochssh")
        val existing = SessionRegistry.Session(4, 1, "host",
            remoteCommand = RemoteSessions.automatic(RemoteSessionModes.TMUX))
        val plan = RemoteSessionConnections.plan(1, listOf(scanned), listOf(existing)) { error("Duplicate") }
        assertEquals(4L, plan.single().id)
    }

    @Test
    fun aScreenSessionOpenedByNameMatchesTheScanEntry() {
        val existing = SessionRegistry.Session(6, 1, "host",
            remoteCommand = RemoteSessions.create(RemoteSessionModes.SCREEN, "build"))
        val plan = RemoteSessionConnections.plan(1, listOf(entry.copy(sessionName = "build")), listOf(existing)) { error("Duplicate") }
        assertEquals(6L, plan.single().id)
    }

    @Test
    fun aDifferentSessionWithTheSameNameOnAnotherHostIsNotMatched() {
        val scanned = RemoteSessionEntry(RemoteSessionModes.TMUX, "\$5", "loch42", false, "loch42")
        val other = SessionRegistry.Session(9, 2, "other",
            remoteCommand = RemoteSessions.create(RemoteSessionModes.TMUX, "loch42"))
        val plan = RemoteSessionConnections.plan(1, listOf(scanned), listOf(other)) { 77 }
        assertEquals(77L, plan.single().id)
        assertTrue(plan.single().connect)
    }
}
