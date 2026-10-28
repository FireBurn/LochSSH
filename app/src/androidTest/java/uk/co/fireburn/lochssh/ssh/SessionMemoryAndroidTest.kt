package uk.co.fireburn.lochssh.ssh

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionMemoryAndroidTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun remembersAndForgetsASessionAcrossInstances() {
        val id = 987_654L
        SessionMemory(context).remember(id, 4, "tmux new-session -A -s lochssh")
        val found = SessionMemory(context).find(id)
        assertEquals(4L, found?.hostId)
        assertEquals("tmux new-session -A -s lochssh", found?.remoteCommand)
        SessionMemory(context).forget(id)
        assertNull(SessionMemory(context).find(id))
    }

    @Test
    fun listsRememberedSessionsAndTheOneLastViewed() {
        val memory = SessionMemory(context)
        memory.remember(987_660L, 2, null)
        memory.view(987_660L)
        assertEquals(2L, memory.all()[987_660L]?.hostId)
        assertEquals(987_660L, memory.lastViewed())
        memory.view(null)
        assertNull(memory.lastViewed())
        memory.forget(987_660L)
    }

    @Test
    fun forgetsEntriesOlderThanThreeDays() {
        val id = 987_655L
        val old = RememberedSession(1, null, System.currentTimeMillis() - 4L * 24 * 60 * 60 * 1000)
        context.getSharedPreferences("lochssh_sessions", android.content.Context.MODE_PRIVATE)
            .edit().putString(id.toString(), old.encode()).commit()
        assertNull(SessionMemory(context).find(id))
    }
}
