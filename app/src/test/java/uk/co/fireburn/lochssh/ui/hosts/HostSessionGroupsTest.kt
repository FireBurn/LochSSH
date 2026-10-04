package uk.co.fireburn.lochssh.ui.hosts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uk.co.fireburn.lochssh.data.db.SshHostEntity
import uk.co.fireburn.lochssh.ssh.SessionRegistry

class HostSessionGroupsTest {
    private fun session(id: Long, host: Long) = SessionRegistry.Session(id, host, "Old host name", remoteName = "claude")

    @Test fun groupsInterleavedSessionsWithoutMergingHostsWithTheSameName() {
        val hosts = listOf(SshHostEntity(1, "Server", "one.test"), SshHostEntity(2, "Server", "two.test", 2222))
        val groups = HostSessionGroups.create(hosts, listOf(session(1, 1), session(2, 2), session(3, 1)))
        assertEquals(listOf(1L, 2L), groups.map { it.hostId })
        assertEquals(listOf(1L, 3L), groups.first().sessions.map { it.id })
        assertEquals("one.test:22", groups.first().address)
        assertEquals("two.test:2222", groups.last().address)
    }

    @Test fun usesCurrentNamesAndKeepsSessionsForDeletedHosts() {
        val groups = HostSessionGroups.create(listOf(SshHostEntity(1, "Quark", "quark.test")),
            listOf(session(1, 1), session(2, 2).copy(error = "Dropped")))
        assertEquals("Quark", groups.first().name)
        assertEquals("Old host name", groups.last().name)
        assertNull(groups.last().address)
        assertEquals("Dropped", groups.last().sessions.single().error)
    }

    @Test fun doesNotShowEmptyGroups() {
        assertEquals(emptyList<HostSessionGroup>(), HostSessionGroups.create(listOf(SshHostEntity(1, "Quark", "quark.test")), emptyList()))
    }
}
