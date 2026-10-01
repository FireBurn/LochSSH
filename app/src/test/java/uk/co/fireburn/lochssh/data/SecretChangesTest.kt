package uk.co.fireburn.lochssh.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SecretChangesTest {
    @Test
    fun keepsChangesTogetherAcrossSuspension() = runBlocking {
        withTimeout(5_000) {
            val changes = SecretChanges()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val calls = mutableListOf<String>()
            val first = launch {
                changes.run {
                    calls += "first-start"
                    entered.complete(Unit)
                    release.await()
                    calls += "first-end"
                }
            }
            entered.await()
            val second = launch { changes.run { calls += "second" } }
            yield()
            assertEquals(listOf("first-start"), calls)
            release.complete(Unit)
            first.join()
            second.join()
            assertEquals(listOf("first-start", "first-end", "second"), calls)
        }
    }

    @Test
    fun cancelledWaiterDoesNotEnterOrKeepTheLock() = runBlocking {
        withTimeout(5_000) {
            val changes = SecretChanges()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val owner = launch { changes.run { entered.complete(Unit); release.await() } }
            entered.await()
            var waiterEntered = false
            val waiter = launch { changes.run { waiterEntered = true } }
            yield()
            waiter.cancel()
            waiter.join()
            release.complete(Unit)
            owner.join()
            assertFalse(waiterEntered)
            assertEquals("next", changes.run { "next" })
        }
    }
}
