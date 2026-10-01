package uk.co.fireburn.lochssh.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Test
import uk.co.fireburn.lochssh.data.db.IdentityEntity
import uk.co.fireburn.lochssh.data.db.LochSshDatabase
import uk.co.fireburn.lochssh.data.db.SecretCleanupEntity
import uk.co.fireburn.lochssh.data.db.SshHostEntity

class ClearDataAndroidTest {
    @Test
    fun failedRowDeletionRestoresSavedRowsAndCleanupList() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, LochSshDatabase::class.java).build()
        try {
            val identity = database.identityDao().insert(IdentityEntity(name = "test",
                authType = "PASSWORD", secretRef = "password"))
            val host = database.sshHostDao().insert(SshHostEntity(name = "test",
                host = "localhost", identityId = identity))
            database.secretCleanupDao().add(SecretCleanupEntity("old"))
            database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER fail_clear BEFORE DELETE ON identities BEGIN SELECT RAISE(ABORT, 'test failure'); END"
            )
            var failed = false
            try {
                database.clearDataDao().clearSavedData()
            } catch (_: android.database.sqlite.SQLiteException) {
                failed = true
            }
            assertTrue(failed)
            assertNotNull(database.identityDao().getById(identity))
            assertNotNull(database.sshHostDao().getById(host))
            assertEquals(listOf("old"), database.secretCleanupDao().pending())
            assertTrue(database.secretCleanupDao().isUsed("password"))
        } finally {
            database.close()
        }
    }

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
