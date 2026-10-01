package uk.co.fireburn.lochssh.ui.identities

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import uk.co.fireburn.lochssh.data.db.AuthTypes
import uk.co.fireburn.lochssh.data.db.IdentityEntity

class IdentityLookupTest {
    private val identity = IdentityEntity(7, "Test", "user", AuthTypes.NONE)

    @Test
    fun newIdentityDoesNotLookUpARow() = runBlocking {
        val result = IdentityLookup.load(0) { error("Unexpected lookup") }
        assertNull(result.identity)
        assertNull(result.error)
    }

    @Test
    fun loadsTheRequestedIdentity() = runBlocking {
        val result = IdentityLookup.load(7) { assertEquals(7L, it); identity }
        assertEquals(identity, result.identity)
        assertNull(result.error)
    }

    @Test
    fun missingIdentityIsNotANewIdentity() = runBlocking {
        val result = IdentityLookup.load(7) { null }
        assertNull(result.identity)
        assertEquals("This identity no longer exists", result.error)
    }

    @Test
    fun reportsStorageErrors() = runBlocking {
        val result = IdentityLookup.load(7) { throw IllegalStateException("disk error") }
        assertNull(result.identity)
        assertEquals("Could not load identity: disk error", result.error)
    }

    @Test
    fun rejectsInvalidIdsWithoutLookingUpARow() = runBlocking {
        assertEquals("Invalid identity", IdentityLookup.load(-1) { error("Unexpected lookup") }.error)
    }

    @Test
    fun preservesCancellation() {
        assertThrows(CancellationException::class.java) {
            runBlocking { IdentityLookup.load(7) { throw CancellationException() } }
        }
    }
}
