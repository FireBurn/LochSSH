package uk.co.fireburn.lochssh.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SecretCleanupTest {
    @Test
    fun keepsLiveSecretsAndRemovesTheirQueueEntries() = runBlocking {
        val deleted = mutableListOf<String>()
        val removed = mutableListOf<String>()
        val remaining = SecretCleanup.retry(
            listOf("live", "unused", "unused"),
            { it == "live" },
            { deleted += it },
            { removed += it }
        )
        assertEquals(listOf("unused"), deleted)
        assertEquals(listOf("live", "unused"), removed)
        assertTrue(remaining.isEmpty())
    }

    @Test
    fun failedUsageCheckNeverDeletesTheSecret() = runBlocking {
        var deleted = false
        var removed = false
        val remaining = SecretCleanup.retry(
            listOf("unknown"),
            { throw IOException("database unavailable") },
            { deleted = true },
            { removed = true }
        )
        assertEquals(listOf("unknown"), remaining)
        assertTrue(!deleted && !removed)
    }

    @Test
    fun failedDeletionKeepsItsEntryAndContinues() = runBlocking {
        val removed = mutableListOf<String>()
        val remaining = SecretCleanup.retry(
            listOf("failed", "next"),
            { false },
            { if (it == "failed") throw IOException("storage unavailable") },
            { removed += it }
        )
        assertEquals(listOf("failed"), remaining)
        assertEquals(listOf("next"), removed)
    }

    @Test
    fun failedQueueRemovalCanRetryAnAlreadyDeletedSecret() = runBlocking {
        val stored = mutableSetOf("unused")
        val first = SecretCleanup.retry(
            listOf("unused"), { false }, { stored.remove(it) },
            { throw IOException("database unavailable") }
        )
        assertEquals(listOf("unused"), first)
        assertTrue(stored.isEmpty())
        val second = SecretCleanup.retry(first, { false }, { stored.remove(it) }, {})
        assertTrue(second.isEmpty())
    }

    @Test
    fun cancellationStopsWithoutRemovingAnEntry() = runBlocking {
        val deleted = mutableListOf<String>()
        val removed = mutableListOf<String>()
        try {
            SecretCleanup.retry(
                listOf("cancelled", "next"),
                { throw CancellationException("cancelled") },
                { deleted += it },
                { removed += it }
            )
            throw AssertionError("Cancellation was swallowed")
        } catch (_: CancellationException) {
            assertTrue(deleted.isEmpty())
            assertTrue(removed.isEmpty())
        }
    }
}
