package uk.co.fireburn.lochssh.data

import android.content.Context
import androidx.room.Room
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.platform.app.InstrumentationRegistry
import java.security.KeyStore
import java.util.UUID
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.fireburn.lochssh.data.db.AuthTypes
import uk.co.fireburn.lochssh.data.db.IdentityEntity
import uk.co.fireburn.lochssh.data.db.LochSshDatabase
import uk.co.fireburn.lochssh.data.db.SecretCleanupEntity

class EncryptedStorageAndroidTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val id = UUID.randomUUID().toString()
    private val fileName = "encrypted-storage-test-$id"
    private val alias = "lochssh-storage-test-$id"

    private fun open(): EncryptedStorageManager {
        val key = MasterKey.Builder(context, alias).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val preferences = EncryptedSharedPreferences.create(context, fileName, key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
        return EncryptedStorageManager(preferences)
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
            reopened.change {
                assertTrue(SecretCleanup.retry(pending.pending(), pending::isUsed,
                    reopened::deleteSecret, pending::remove).isEmpty())
            }
            assertTrue(pending.pending().isEmpty())
            assertNull(open().getSecret("unused"))
            assertEquals("dummy private key", open().getSecret("key"))
            assertEquals("dummy passphrase", open().getSecret("passphrase"))
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }
}
