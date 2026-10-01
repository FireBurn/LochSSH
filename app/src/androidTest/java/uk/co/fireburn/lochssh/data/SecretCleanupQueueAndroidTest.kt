package uk.co.fireburn.lochssh.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import uk.co.fireburn.lochssh.data.db.AuthTypes
import uk.co.fireburn.lochssh.data.db.IdentityEntity
import uk.co.fireburn.lochssh.data.db.LochSshDatabase
import uk.co.fireburn.lochssh.data.db.SecretCleanupEntity

class SecretCleanupQueueAndroidTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "secret-cleanup-test-${UUID.randomUUID()}.db"
    private var database = open()

    private fun open() = Room.databaseBuilder(context, LochSshDatabase::class.java, name).build()

    @After
    fun close() {
        database.close()
        context.deleteDatabase(name)
    }

    @Test
    fun retainsUniqueReferencesAfterReopening() = runBlocking {
        database.secretCleanupDao().add(SecretCleanupEntity("unused"))
        database.secretCleanupDao().add(SecretCleanupEntity("unused"))
        database.close()
        database = open()
        assertEquals(listOf("unused"), database.secretCleanupDao().pending())
        database.secretCleanupDao().remove("unused")
        database.secretCleanupDao().remove("unused")
        assertTrue(database.secretCleanupDao().pending().isEmpty())
    }

    @Test
    fun findsReferencesUsedByEitherCredentialField() = runBlocking {
        val identity = IdentityEntity(name = "Test", username = "user", authType = AuthTypes.PUBLIC_KEY,
            keyMaterialRef = "key", secretRef = "passphrase")
        val id = database.identityDao().insert(identity)
        val cleanup = database.secretCleanupDao()
        assertTrue(cleanup.isUsed("key"))
        assertTrue(cleanup.isUsed("passphrase"))
        assertFalse(cleanup.isUsed("unused"))
        cleanup.add(SecretCleanupEntity("key"))
        cleanup.remove("key")
        assertNotNull(database.identityDao().getById(id))
        database.identityDao().deleteIfUnused(id)
        assertFalse(cleanup.isUsed("key"))
        assertFalse(cleanup.isUsed("passphrase"))
    }
}
