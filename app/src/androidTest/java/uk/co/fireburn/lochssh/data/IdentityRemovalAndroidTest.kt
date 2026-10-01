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
}
