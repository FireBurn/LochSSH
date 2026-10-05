package uk.co.fireburn.lochssh.ssh

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.fireburn.lochssh.data.db.RemoteSessionModes

class RemoteSessionConnectionsAndroidTest {
    private val entry = RemoteSessionEntry(RemoteSessionModes.TMUX, "\$1", "work", false)

    private fun disconnected(): SessionRegistry.Session {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = SshConnectionConfig("localhost", 22, "test", "none", null, null,
            null, null, 30)
        val manager = SshConnectionManager(context, config, onExit = {})
        return SessionRegistry.Session(7, 1, "host", manager = manager,
            remoteCommand = RemoteSessions.attach(entry))
    }

    @Test fun reconnectsADisconnectedManagerBeforeItsErrorIsRecorded() {
        val session = disconnected()
        assertTrue(session.canReconnect)
        val plan = RemoteSessionConnections.plan(1, listOf(entry), listOf(session)) { error("Duplicate") }
        assertEquals(7L, plan.single().id)
        assertTrue(plan.single().connect)
    }

    @Test fun prefersAnOpeningRowOverADisconnectedManagerWithoutAnError() {
        val session = disconnected()
        val opening = session.copy(id = 8, manager = null)
        val plan = RemoteSessionConnections.plan(1, listOf(entry), listOf(session, opening)) { error("Duplicate") }
        assertEquals(8L, plan.single().id)
        assertFalse(plan.single().connect)
    }
}
