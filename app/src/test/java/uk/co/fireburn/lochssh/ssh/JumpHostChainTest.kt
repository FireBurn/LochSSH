package uk.co.fireburn.lochssh.ssh

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import uk.co.fireburn.lochssh.data.db.SshHostEntity

class JumpHostChainTest {
    private fun host(id: Long, jump: Long? = null) = SshHostEntity(id, "host$id", "host$id", jumpHostId = jump)

    @Test
    fun ordersOutermostFirstAndKeepsDirectConnections() = runBlocking {
        val hosts = listOf(host(1, 2), host(2, 3), host(3)).associateBy { it.id }
        assertEquals(listOf(3L, 2L), JumpHostChain.resolve(hosts.getValue(1)) { hosts[it] }.map { it.id })
        assertEquals(emptyList<SshHostEntity>(), JumpHostChain.resolve(host(4)) { null })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMissingHost() { runBlocking { JumpHostChain.resolve(host(1, 2)) { null } } }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsSelfReference() { runBlocking { JumpHostChain.resolve(host(1, 1)) { host(1, 1) } } }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsLongerCycle() { runBlocking {
        val hosts = listOf(host(1, 2), host(2, 3), host(3, 1)).associateBy { it.id }
        JumpHostChain.resolve(hosts.getValue(1)) { hosts[it] }
    } }

    @Test(expected = IllegalArgumentException::class)
    fun limitsChainLength() { runBlocking {
        JumpHostChain.resolve(host(1, 2)) { host(it, it + 1) }
    } }
}
