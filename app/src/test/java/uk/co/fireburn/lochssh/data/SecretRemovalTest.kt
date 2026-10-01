package uk.co.fireburn.lochssh.data

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SecretRemovalTest {
    @Test
    fun removesEachReferenceOnce() {
        val removed = mutableListOf<String>()
        assertEquals(emptyList<String>(), SecretRemoval.remove(listOf("a", "a", "b")) { removed += it })
        assertEquals(listOf("a", "b"), removed)
    }

    @Test
    fun returnsFailuresAndStillTriesOtherReferences() {
        val removed = mutableListOf<String>()
        val remaining = SecretRemoval.remove(listOf("a", "b", "c")) {
            removed += it
            if (it == "b") error("write failed")
        }
        assertEquals(listOf("b"), remaining)
        assertEquals(listOf("a", "b", "c"), removed)
    }

    @Test
    fun preservesCancellation() {
        assertThrows(CancellationException::class.java) {
            SecretRemoval.remove(listOf("a")) { throw CancellationException() }
        }
    }
}
