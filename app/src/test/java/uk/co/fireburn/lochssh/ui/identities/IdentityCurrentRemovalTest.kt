package uk.co.fireburn.lochssh.ui.identities

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.fireburn.lochssh.data.db.AuthTypes
import uk.co.fireburn.lochssh.data.db.IdentityEntity

class IdentityCurrentRemovalTest {
    @Test
    fun loadsCurrentReferencesBeforeDeleting() = runBlocking {
        val calls = mutableListOf<String>()
        assertNull(IdentityRemoval.removeCurrent(7,
            { calls += "load:$it"; IdentityEntity(id = it, name = "Changed",
                authType = AuthTypes.PASSWORD, secretRef = "current") },
            { calls += "delete:$it"; 1 },
            { calls += "secret:$it" }))
        assertEquals(listOf("load:7", "delete:7", "secret:current"), calls)
    }

    @Test
    fun missingRowDoesNotDeleteAnything() = runBlocking {
        val calls = mutableListOf<String>()
        assertEquals("This identity no longer exists", IdentityRemoval.removeCurrent(7,
            { null }, { calls += "delete"; 1 }, { calls += "secret" }))
        assertTrue(calls.isEmpty())
    }

    @Test
    fun failedLookupDoesNotDeleteAnything() = runBlocking {
        val calls = mutableListOf<String>()
        assertEquals("Could not delete identity: read failed", IdentityRemoval.removeCurrent(7,
            { error("read failed") }, { calls += "delete"; 1 }, { calls += "secret" }))
        assertTrue(calls.isEmpty())
    }

    @Test
    fun cancelledLookupDoesNotDeleteAnything() = runBlocking {
        val calls = mutableListOf<String>()
        try {
            IdentityRemoval.removeCurrent(7, { throw CancellationException() },
                { calls += "delete"; 1 }, { calls += "secret" })
            throw AssertionError("Cancellation was swallowed")
        } catch (_: CancellationException) {
            assertTrue(calls.isEmpty())
        }
    }
}
