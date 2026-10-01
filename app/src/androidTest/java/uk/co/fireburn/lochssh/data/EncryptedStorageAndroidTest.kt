package uk.co.fireburn.lochssh.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.platform.app.InstrumentationRegistry
import java.security.KeyStore
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

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
}
