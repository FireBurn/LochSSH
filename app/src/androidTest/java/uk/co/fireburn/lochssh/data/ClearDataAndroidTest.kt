package uk.co.fireburn.lochssh.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.fireburn.lochssh.data.db.IdentityEntity
import uk.co.fireburn.lochssh.data.db.LochSshDatabase
import uk.co.fireburn.lochssh.data.db.SecretCleanupEntity

class ClearDataAndroidTest {
    @Test
    fun keepsExistingAndCurrentSecretReferencesUntilCleanupSucceeds() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, LochSshDatabase::class.java).build()
        try {
            database.secretCleanupDao().add(SecretCleanupEntity("old"))
            database.identityDao().insert(IdentityEntity(name = "test", authType = "KEY",
                secretRef = "password", keyMaterialRef = "key"))
            database.clearDataDao().clearSavedData()
            assertEquals(listOf("key", "old", "password"), database.secretCleanupDao().pending())
            for (reference in database.secretCleanupDao().pending()) {
                assertTrue(!database.secretCleanupDao().isUsed(reference))
            }
            database.clearDataDao().clearSavedData()
            assertEquals(listOf("key", "old", "password"), database.secretCleanupDao().pending())
            database.clearDataDao().clearCleanup()
            assertTrue(database.secretCleanupDao().pending().isEmpty())
        } finally {
            database.close()
        }
    }
}
