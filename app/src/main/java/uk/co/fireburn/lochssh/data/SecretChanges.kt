package uk.co.fireburn.lochssh.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class SecretChanges {
    private val mutex = Mutex()

    suspend fun <T> run(block: suspend () -> T): T = mutex.withLock { block() }
}
