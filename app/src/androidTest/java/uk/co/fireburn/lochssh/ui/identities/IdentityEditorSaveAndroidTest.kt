package uk.co.fireburn.lochssh.ui.identities

import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.fireburn.lochssh.data.EncryptedStorageManager
import uk.co.fireburn.lochssh.data.db.AuthTypes
import uk.co.fireburn.lochssh.data.db.IdentityEntity
import uk.co.fireburn.lochssh.data.db.LochSshDatabase

class IdentityEditorSaveAndroidTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val database = Room.inMemoryDatabaseBuilder(
        instrumentation.targetContext, LochSshDatabase::class.java).build()
    private val store = ViewModelStore()

    @After
    fun close() {
        instrumentation.runOnMainSync { store.clear() }
        database.close()
    }

    private suspend fun load(id: Long): IdentityEditorViewModel {
        lateinit var editor: IdentityEditorViewModel
        instrumentation.runOnMainSync {
            editor = IdentityEditorViewModel(database.identityDao(),
                EncryptedStorageManager(instrumentation.targetContext))
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

    private suspend fun save(editor: IdentityEditorViewModel): String? {
        val finished = CompletableDeferred<String?>()
        instrumentation.runOnMainSync {
            editor.save("Changed", "new-user", AuthTypes.NONE, KeySources.PATH, "", "", "") {
                finished.complete(it)
            }
        }
        return withTimeout(5_000) { finished.await() }
    }

    @Test
    fun savingARemovedIdentityReportsAnErrorWithoutInsertingANewRow() = runBlocking {
        val dao = database.identityDao()
        val id = dao.insert(IdentityEntity(name = "Test", username = "user", authType = AuthTypes.NONE))
        val editor = load(id)
        assertEquals(1, dao.deleteIfUnused(id))
        assertEquals("This identity no longer exists", save(editor))
        assertTrue(dao.observeAll().first().isEmpty())
    }

    @Test
    fun savingAnExistingIdentityUpdatesItsRow() = runBlocking {
        val dao = database.identityDao()
        val id = dao.insert(IdentityEntity(name = "Test", username = "user", authType = AuthTypes.NONE))
        assertNull(save(load(id)))
        val saved = requireNotNull(dao.getById(id))
        assertEquals("Changed", saved.name)
        assertEquals("new-user", saved.username)
        assertEquals(1, dao.observeAll().first().size)
    }
}
