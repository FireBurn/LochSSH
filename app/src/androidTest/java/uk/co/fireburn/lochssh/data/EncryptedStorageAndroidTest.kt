package uk.co.fireburn.lochssh.data

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.platform.app.InstrumentationRegistry
import java.security.KeyStore
import java.util.UUID
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.fireburn.lochssh.data.db.AuthTypes
import uk.co.fireburn.lochssh.data.db.IdentityEntity
import uk.co.fireburn.lochssh.data.db.LochSshDatabase
import uk.co.fireburn.lochssh.data.db.SecretCleanupEntity
import uk.co.fireburn.lochssh.data.db.SshHostEntity

class EncryptedStorageAndroidTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val id = UUID.randomUUID().toString()
    private val fileName = "encrypted-storage-test-$id"
    private val alias = "lochssh-storage-test-$id"

    private fun open(): EncryptedStorageManager = EncryptedStorageManager(openPreferences())

    private fun openPreferences(): SharedPreferences {
        val key = MasterKey.Builder(context, alias).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(context, fileName, key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }

    @After
    fun removeTestStorage() {
        context.deleteSharedPreferences(fileName)
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
    }

    @Test
    fun savesAndReloadsWithoutPlainReferencesOrValues() {
        val storage = open()
        val reference = storage.newReference()
        val value = "dummy passphrase — café 🔑\nsecond line"
        storage.putSecret(reference, value)
        assertEquals(value, open().getSecret(reference))
        val raw = context.getSharedPreferences(fileName, Context.MODE_PRIVATE).all
        assertFalse(raw.containsKey(reference))
        assertFalse(raw.values.contains(value))
        storage.deleteSecret(reference)
        assertNull(open().getSecret(reference))
    }

    @Test
    fun clearsSecretsAndCanSaveAgainWithTheSameKey() {
        val storage = open()
        val first = storage.newReference()
        val second = storage.newReference()
        storage.putSecret(first, "first dummy secret")
        storage.putSecret(second, "second dummy secret")
        storage.clear()
        val reopened = open()
        assertNull(reopened.getSecret(first))
        assertNull(reopened.getSecret(second))
        reopened.putSecret(first, "replacement dummy secret")
        assertEquals("replacement dummy secret", open().getSecret(first))
    }

    @Test
    fun retriesAfterReopeningWithoutDeletingLiveCredentials() = runBlocking {
        val databaseName = "encrypted-cleanup-test-$id.db"
        var database = Room.databaseBuilder(context, LochSshDatabase::class.java, databaseName).build()
        try {
            val storage = open()
            storage.putSecret("key", "dummy private key")
            storage.putSecret("passphrase", "dummy passphrase")
            storage.putSecret("unused", "unused dummy secret")
            database.identityDao().insert(IdentityEntity(name = "Test", username = "user",
                authType = AuthTypes.PUBLIC_KEY, keyMaterialRef = "key", secretRef = "passphrase"))
            val cleanup = database.secretCleanupDao()
            for (reference in listOf("key", "passphrase", "unused")) {
                cleanup.add(SecretCleanupEntity(reference))
            }
            val remaining = storage.change {
                SecretCleanup.retry(cleanup.pending(), cleanup::isUsed,
                    { throw IOException("storage unavailable") }, cleanup::remove)
            }
            assertEquals(listOf("unused"), remaining)
            database.close()
            database = Room.databaseBuilder(context, LochSshDatabase::class.java, databaseName).build()
            val reopened = open()
            val pending = database.secretCleanupDao()
            assertEquals(listOf("unused"), pending.pending())
            val recovery = SecretRecovery(database, reopened)
            recovery.retry()
            assertNull(recovery.error.value)
            assertFalse(recovery.running.value)
            assertTrue(pending.pending().isEmpty())
            assertNull(open().getSecret("unused"))
            assertEquals("dummy private key", open().getSecret("key"))
            assertEquals("dummy passphrase", open().getSecret("passphrase"))
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun deletionKeepsSecretsSharedWithAnotherIdentity() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, LochSshDatabase::class.java).build()
        try {
            val storage = open()
            storage.putSecret("shared", "dummy shared passphrase")
            storage.putSecret("private", "dummy private key")
            val first = database.identityDao().insert(IdentityEntity(name = "First",
                authType = AuthTypes.PUBLIC_KEY, secretRef = "shared", keyMaterialRef = "private"))
            val second = database.identityDao().insert(IdentityEntity(name = "Second",
                authType = AuthTypes.PASSWORD, secretRef = "shared"))
            assertNull(IdentityDeletion(database, storage).delete(first))
            assertNull(database.identityDao().getById(first))
            assertNull(storage.getSecret("private"))
            assertEquals("dummy shared passphrase", storage.getSecret("shared"))
            assertTrue(database.secretCleanupDao().pending().isEmpty())
            assertNull(IdentityDeletion(database, storage).delete(second))
            assertNull(storage.getSecret("shared"))
        } finally {
            database.close()
        }
    }

    @Test
    fun blockedDeletionKeepsTheRowAndDoesNotQueueItsSecrets() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, LochSshDatabase::class.java).build()
        try {
            val storage = open()
            storage.putSecret("used", "dummy passphrase")
            val id = database.identityDao().insert(IdentityEntity(name = "Test",
                authType = AuthTypes.PASSWORD, secretRef = "used"))
            database.sshHostDao().insert(SshHostEntity(name = "Test", host = "test.invalid", identityId = id))
            assertEquals("Could not delete identity: Identity is used by a saved host",
                IdentityDeletion(database, storage).delete(id))
            assertEquals("used", database.identityDao().getById(id)?.secretRef)
            assertEquals("dummy passphrase", storage.getSecret("used"))
            assertTrue(database.secretCleanupDao().pending().isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun failedQueueWriteRollsBackIdentityDeletion() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, LochSshDatabase::class.java).build()
        try {
            val storage = open()
            storage.putSecret("kept", "dummy passphrase")
            val id = database.identityDao().insert(IdentityEntity(name = "Test",
                authType = AuthTypes.PASSWORD, secretRef = "kept"))
            database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER fail_cleanup BEFORE INSERT ON secret_cleanup " +
                    "BEGIN SELECT RAISE(ABORT, 'queue write failed'); END"
            )
            val error = IdentityDeletion(database, storage).delete(id)
            assertTrue(error?.startsWith("Could not delete identity:") == true)
            assertEquals("kept", database.identityDao().getById(id)?.secretRef)
            assertEquals("dummy passphrase", storage.getSecret("kept"))
            assertTrue(database.secretCleanupDao().pending().isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun recoveryReportsFailedRemovalAndClearsTheErrorAfterRetry() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, LochSshDatabase::class.java).build()
        try {
            val preferences = openPreferences()
            var failRemoval = true
            val failing = object : SharedPreferences by preferences {
                override fun edit(): SharedPreferences.Editor {
                    val editor = preferences.edit()
                    return object : SharedPreferences.Editor by editor {
                        override fun remove(key: String?): SharedPreferences.Editor {
                            if (!failRemoval) editor.remove(key)
                            return this
                        }
                        override fun commit(): Boolean = if (failRemoval) false else editor.commit()
                    }
                }
            }
            open().putSecret("unused", "unused dummy secret")
            database.secretCleanupDao().add(SecretCleanupEntity("unused"))
            val recovery = SecretRecovery(database, EncryptedStorageManager(failing))
            recovery.retry()
            assertNotNull(recovery.error.value)
            assertFalse(recovery.running.value)
            assertEquals(listOf("unused"), database.secretCleanupDao().pending())
            assertEquals("unused dummy secret", open().getSecret("unused"))
            failRemoval = false
            recovery.retry()
            assertNull(recovery.error.value)
            assertFalse(recovery.running.value)
            assertTrue(database.secretCleanupDao().pending().isEmpty())
            assertNull(open().getSecret("unused"))
        } finally {
            database.close()
        }
    }

    @Test
    fun recoveryReportsDatabaseFailureWithoutRemovingSecrets() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, LochSshDatabase::class.java).build()
        val storage = open()
        storage.putSecret("kept", "dummy passphrase")
        database.identityDao().insert(IdentityEntity(name = "Test",
            authType = AuthTypes.PASSWORD, secretRef = "kept"))
        database.close()
        val recovery = SecretRecovery(database, storage)
        recovery.retry()
        assertEquals("Could not check stored secret cleanup. Try again.", recovery.error.value)
        assertFalse(recovery.running.value)
        assertEquals("dummy passphrase", storage.getSecret("kept"))
    }

    @Test
    fun cancelledRecoveryDoesNotRemoveSecretsOrReportAStorageError() = runBlocking {
        withTimeout(5_000) {
            val database = Room.inMemoryDatabaseBuilder(context, LochSshDatabase::class.java).build()
            try {
                val storage = open()
                storage.putSecret("unused", "dummy secret")
                database.secretCleanupDao().add(SecretCleanupEntity("unused"))
                val entered = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val owner = launch { storage.change { entered.complete(Unit); release.await() } }
                entered.await()
                val recovery = SecretRecovery(database, storage)
                val started = CompletableDeferred<Unit>()
                val retry = launch { started.complete(Unit); recovery.retry() }
                started.await()
                retry.cancel()
                retry.join()
                release.complete(Unit)
                owner.join()
                assertTrue(retry.isCancelled)
                assertFalse(recovery.running.value)
                assertNull(recovery.error.value)
                assertEquals(listOf("unused"), database.secretCleanupDao().pending())
                assertEquals("dummy secret", storage.getSecret("unused"))
            } finally {
                database.close()
            }
        }
    }
}
