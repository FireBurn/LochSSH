package uk.co.fireburn.lochssh.ui.identities

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.fireburn.lochssh.data.db.AuthTypes
import uk.co.fireburn.lochssh.data.db.IdentityEntity

class IdentityRemovalTest {
    private val identity = IdentityEntity(7, "Test", "user", AuthTypes.PUBLIC_KEY,
        keyMaterialRef = "key", secretRef = "password")

    @Test
    fun deletesTheRowBeforeItsSecrets() = runBlocking {
        val calls = mutableListOf<String>()
        assertNull(IdentityRemoval.remove(identity, { calls += "row-$it"; 1 }, { calls += it }))
        assertEquals(listOf("row-7", "password", "key"), calls)
    }

    @Test
    fun keepsSecretsWhenTheRowIsStillUsed() = runBlocking {
        val removed = mutableListOf<String>()
        val error = IdentityRemoval.remove(identity, { 0 }, removed::add)
        assertTrue(error!!.contains("used by a saved host"))
        assertTrue(removed.isEmpty())
    }

    @Test
    fun keepsSecretsWhenTheDatabaseFails() = runBlocking {
        val removed = mutableListOf<String>()
        val error = IdentityRemoval.remove(identity, { throw IllegalStateException("disk full") }, removed::add)
        assertEquals("Could not delete identity: disk full", error)
        assertTrue(removed.isEmpty())
    }

    @Test
    fun reportsCleanupFailureAndTriesTheOtherSecret() = runBlocking {
        val removed = mutableListOf<String>()
        val error = IdentityRemoval.remove(identity, { 1 }, {
            removed += it
            if (it == "password") throw IllegalStateException("write failed")
        })
        assertEquals(listOf("password", "key"), removed)
        assertEquals("Identity deleted, but could not remove all stored secrets", error)
    }

    @Test
    fun doesNotTurnCancellationIntoAnError() {
        assertThrows(CancellationException::class.java) {
            runBlocking { IdentityRemoval.remove(identity, { throw CancellationException() }, {}) }
        }
    }
}
