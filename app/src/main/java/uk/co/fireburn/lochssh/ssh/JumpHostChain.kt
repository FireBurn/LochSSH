package uk.co.fireburn.lochssh.ssh

import uk.co.fireburn.lochssh.data.db.SshHostEntity

object JumpHostChain {
    suspend fun resolve(host: SshHostEntity, find: suspend (Long) -> SshHostEntity?): List<SshHostEntity> {
        val seen = mutableSetOf(host.id)
        val chain = mutableListOf<SshHostEntity>()
        var next = host.jumpHostId
        while (next != null) {
            require(seen.add(next)) { "Jump hosts form a loop" }
            require(chain.size < 8) { "Use no more than eight jump hosts" }
            val jump = find(next) ?: throw IllegalArgumentException("Jump host no longer exists")
            chain += jump
            next = jump.jumpHostId
        }
        return chain.asReversed()
    }
}
