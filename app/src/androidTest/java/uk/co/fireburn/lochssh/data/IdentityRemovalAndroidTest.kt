package uk.co.fireburn.lochssh.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import uk.co.fireburn.lochssh.data.db.AuthTypes
import uk.co.fireburn.lochssh.data.db.IdentityEntity
import uk.co.fireburn.lochssh.data.db.LochSshDatabase
import uk.co.fireburn.lochssh.data.db.SshHostEntity
import uk.co.fireburn.lochssh.ui.identities.IdentityRemoval

class IdentityRemovalAndroidTest {
    private val database = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        LochSshDatabase::class.java
    ).build()

    @After
    fun closeDatabase() { database.close() }

    @Test
    fun keepsAnIdentityUntilEveryHostStopsUsingIt() = runBlocking {
        val identities = database.identityDao()
        val hosts = database.sshHostDao()
        val id = identities.insert(IdentityEntity(name = "Test", authType = AuthTypes.NONE))
        val first = SshHostEntity(name = "First", host = "first.invalid", identityId = id)
        val second = SshHostEntity(name = "Second", host = "second.invalid", identityId = id)
        val firstId = hosts.insert(first)
        val secondId = hosts.insert(second)

        assertEquals(0, identities.deleteIfUnused(id))
        assertNotNull(identities.getById(id))
        hosts.delete(first.copy(id = firstId))
        assertEquals(0, identities.deleteIfUnused(id))
        hosts.update(second.copy(id = secondId, identityId = null))
        assertEquals(1, identities.deleteIfUnused(id))
        assertNull(identities.getById(id))
        assertEquals(0, identities.deleteIfUnused(id))
        assertNotNull(hosts.getById(secondId))
    }

    @Test
    fun removesCurrentCredentialsRatherThanAnOldListSnapshot() = runBlocking {
        val identities = database.identityDao()
        val original = IdentityEntity(name = "Test", authType = AuthTypes.PUBLIC_KEY,
            keyMaterialRef = "old-key", secretRef = "old-passphrase")
        val id = identities.insert(original)
        val snapshot = original.copy(id = id)
        identities.update(snapshot.copy(keyMaterialRef = "new-key", secretRef = "new-passphrase"))
        val deleted = mutableListOf<String>()
        assertNull(IdentityRemoval.removeCurrent(snapshot.id, identities::getById,
            identities::deleteIfUnused) { deleted += it })
        assertEquals(listOf("new-passphrase", "new-key"), deleted)
        assertNull(identities.getById(id))
    }

    @Test
    fun missingCurrentRowDoesNotDeleteSecrets() = runBlocking {
        val identities = database.identityDao()
        val deleted = mutableListOf<String>()
        assertEquals("This identity no longer exists",
            IdentityRemoval.removeCurrent(99, identities::getById,
                identities::deleteIfUnused) { deleted += it })
        assertEquals(emptyList<String>(), deleted)
    }
}
