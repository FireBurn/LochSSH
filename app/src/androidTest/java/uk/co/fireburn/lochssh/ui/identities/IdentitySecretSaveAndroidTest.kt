package uk.co.fireburn.lochssh.ui.identities

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uk.co.fireburn.lochssh.data.EncryptedStorageManager
import uk.co.fireburn.lochssh.data.db.AuthTypes
import uk.co.fireburn.lochssh.data.db.IdentityDao
import uk.co.fireburn.lochssh.data.db.IdentityEntity
import uk.co.fireburn.lochssh.data.db.LochSshDatabase

class IdentitySecretSaveAndroidTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val fileName = "identity-secret-test-${UUID.randomUUID()}"
    private val preferences = context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
    private val database = Room.inMemoryDatabaseBuilder(context, LochSshDatabase::class.java).build()
    private val store = ViewModelStore()
    private val original = IdentityEntity(name = "Original", username = "user",
        authType = AuthTypes.PUBLIC_KEY, keyMaterialRef = "old-key", secretRef = "old-secret")
    private var id = 0L

    @After
    fun close() {
        instrumentation.runOnMainSync { store.clear() }
        database.close()
        context.deleteSharedPreferences(fileName)
    }

    private suspend fun load(dao: IdentityDao, preferences: SharedPreferences): IdentityEditorViewModel {
        id = database.identityDao().insert(original)
        this.preferences.edit().putString("old-key", "original key")
            .putString("old-secret", "original passphrase").commit()
        lateinit var editor: IdentityEditorViewModel
        instrumentation.runOnMainSync {
            editor = IdentityEditorViewModel(dao, EncryptedStorageManager(preferences))
            store.put("editor", editor)
            editor.load(id)
        }
        withTimeout(5_000) {
            var loaded = false
            while (!loaded) {
                instrumentation.runOnMainSync { loaded = editor.loaded }
                if (!loaded) delay(10)
            }
        }
        return editor
    }

    private suspend fun save(editor: IdentityEditorViewModel, source: String = KeySources.PASTE): String? {
        val finished = CompletableDeferred<String?>()
        instrumentation.runOnMainSync {
            editor.save("Changed", "user", AuthTypes.PUBLIC_KEY, source,
                if (source == KeySources.PATH) "/test/key" else "",
                if (source == KeySources.PASTE) "replacement key" else "",
                if (source == KeySources.PASTE) "replacement passphrase" else "") { finished.complete(it) }
        }
        return withTimeout(5_000) { finished.await() }
    }

    private suspend fun assertOriginalIsKept() {
        assertEquals(original.copy(id = id), database.identityDao().getById(id))
        assertEquals(mapOf("old-key" to "original key", "old-secret" to "original passphrase"), preferences.all)
    }

    @Test
    fun databaseFailureRemovesNewSecretsAndKeepsOldCredentials() = runBlocking {
        val dao = object : IdentityDao by database.identityDao() {
            override suspend fun update(identity: IdentityEntity): Int = error("database write failed")
        }
        assertEquals("database write failed", save(load(dao, preferences)))
        assertOriginalIsKept()
    }

    @Test
    fun failedSecretWriteKeepsOldCredentialsEvenIfTheWriteWasApplied() = runBlocking {
        val failing = object : SharedPreferences by preferences {
            var first = true
            override fun edit(): SharedPreferences.Editor {
                val editor = preferences.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                        editor.putString(key, value)
                        return this
                    }
                    override fun commit(): Boolean {
                        val result = editor.commit()
                        if (!first) return result
                        first = false
                        return false
                    }
                }
            }
        }
        assertEquals("Could not save secret", save(load(database.identityDao(), failing)))
        assertOriginalIsKept()
    }

    @Test
    fun failedOldKeyCleanupReportsTheSavedRowAndAllowsRetry() = runBlocking {
        val failing = object : SharedPreferences by preferences {
            override fun edit(): SharedPreferences.Editor {
                val editor = preferences.edit()
                var blocked = false
                return object : SharedPreferences.Editor by editor {
                    override fun remove(key: String?): SharedPreferences.Editor {
                        if (key == "old-key") blocked = true else editor.remove(key)
                        return this
                    }
                    override fun commit(): Boolean = if (blocked) false else editor.commit()
                }
            }
        }
        val editor = load(database.identityDao(), failing)
        assertEquals("Identity saved, but could not remove old stored secrets", save(editor, KeySources.PATH))
        val saved = requireNotNull(database.identityDao().getById(id))
        assertEquals("/test/key", saved.keyPath)
        assertNull(saved.keyMaterialRef)
        instrumentation.runOnMainSync { assertEquals(saved, editor.identity) }
        assertEquals(mapOf("old-key" to "original key", "old-secret" to "original passphrase"), preferences.all)
        assertNull(save(editor, KeySources.PATH))
        assertEquals(saved, database.identityDao().getById(id))
    }

    @Test
    fun switchingToAKeyPathRemovesOnlyThePastedKey() = runBlocking {
        assertNull(save(load(database.identityDao(), preferences), KeySources.PATH))
        val saved = requireNotNull(database.identityDao().getById(id))
        assertEquals("/test/key", saved.keyPath)
        assertNull(saved.keyMaterialRef)
        assertEquals("old-secret", saved.secretRef)
        assertEquals(mapOf("old-secret" to "original passphrase"), preferences.all)
    }
}
