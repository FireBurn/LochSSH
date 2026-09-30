package uk.co.fireburn.lochssh.ui.hosts

import org.junit.Assert.assertEquals
import org.junit.Test

class HostShortcutCleanupTest {
    private val ids = listOf("host-1", "host-2", "other", "host-0", "host--1", "host-invalid")

    @Test
    fun deletingOneHostKeepsOtherShortcuts() {
        assertEquals(listOf("host-2"), HostShortcuts.idsForRemoval(ids, 2))
        assertEquals(emptyList<String>(), HostShortcuts.idsForRemoval(ids, 9))
    }

    @Test
    fun clearingDataSelectsOnlySavedHostShortcuts() {
        assertEquals(listOf("host-1", "host-2"), HostShortcuts.idsForRemoval(ids))
        assertEquals(emptyList<String>(), HostShortcuts.idsForRemoval(emptyList()))
    }
}
