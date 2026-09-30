package uk.co.fireburn.lochssh.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionShortcutsTest {
    @Test
    fun cyclesBothWaysAndWrapsAtTheEnds() {
        val ids = listOf(11L, 12L, 13L)
        assertEquals(12L, SessionShortcuts.destination(ids, 11, 1))
        assertEquals(11L, SessionShortcuts.destination(ids, 12, -1))
        assertEquals(11L, SessionShortcuts.destination(ids, 13, 1))
        assertEquals(13L, SessionShortcuts.destination(ids, 11, -1))
    }

    @Test
    fun emptyAndSingleSessionListsDoNotNavigate() {
        assertNull(SessionShortcuts.destination(emptyList(), 11, 1))
        assertNull(SessionShortcuts.destination(listOf(11), 11, -1))
    }

    @Test
    fun missingCurrentSessionUsesTheNearestEnd() {
        assertEquals(11L, SessionShortcuts.destination(listOf(11, 12), 99, 1))
        assertEquals(12L, SessionShortcuts.destination(listOf(11, 12), 99, -1))
    }
}
