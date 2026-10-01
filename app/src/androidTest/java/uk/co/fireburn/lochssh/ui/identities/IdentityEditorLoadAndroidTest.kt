package uk.co.fireburn.lochssh.ui.identities

import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.fireburn.lochssh.data.EncryptedStorageManager
import uk.co.fireburn.lochssh.data.db.AuthTypes
import uk.co.fireburn.lochssh.data.db.IdentityDao
import uk.co.fireburn.lochssh.data.db.IdentityEntity

class IdentityEditorLoadAndroidTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val store = ViewModelStore()
    private val dao = TestDao()
    private lateinit var editor: IdentityEditorViewModel

    private fun start() = instrumentation.runOnMainSync {
        editor = IdentityEditorViewModel(dao, EncryptedStorageManager(instrumentation.targetContext))
        store.put("editor", editor)
    }

    @After
    fun close() = instrumentation.runOnMainSync { store.clear() }

    @Test
    fun missingIdentityCannotBeSavedAsANewOne() {
        start()
        instrumentation.runOnMainSync {
            editor.load(7)
            assertFalse(editor.loaded)
            assertEquals("This identity no longer exists", editor.loadError)
            var error: String? = null
            editor.save("Test", "user", AuthTypes.NONE, KeySources.PATH, "", "", "") { error = it }
            assertEquals(editor.loadError, error)
            assertEquals(0, dao.writes)
        }
    }

    @Test
    fun failedLookupCanBeRetried() {
        start()
        instrumentation.runOnMainSync {
            dao.lookup = { throw IllegalStateException("read failed") }
            editor.load(7)
            assertFalse(editor.loaded)
            assertTrue(editor.loadError!!.contains("read failed"))
            val identity = IdentityEntity(7, "Test", "user", AuthTypes.NONE)
            dao.lookup = { identity }
            editor.load(7)
            assertTrue(editor.loaded)
            assertNull(editor.loadError)
            assertEquals(identity, editor.identity)
        }
    }

    @Test
    fun cancelledLookupCannotReplaceTheNextIdentity() {
        val old = CompletableDeferred<IdentityEntity>()
        val returned = CompletableDeferred<Unit>()
        val next = IdentityEntity(2, "Next", "user", AuthTypes.NONE)
        dao.lookup = { id ->
            if (id == 1L) withContext(NonCancellable) {
                old.await().also { returned.complete(Unit) }
            } else next
        }
        start()
        instrumentation.runOnMainSync {
            editor.load(1)
            assertFalse(editor.loaded)
            editor.load(2)
            assertEquals(next, editor.identity)
        }
        old.complete(IdentityEntity(1, "Old", "user", AuthTypes.NONE))
        runBlocking { withTimeout(5_000) { returned.await() } }
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            assertTrue(editor.loaded)
            assertEquals(next, editor.identity)
            assertNull(editor.loadError)
        }
    }

    private class TestDao : IdentityDao {
        var lookup: suspend (Long) -> IdentityEntity? = { null }
        var writes = 0
        override fun observeAll() = flowOf(emptyList<IdentityEntity>())
        override suspend fun getById(id: Long) = lookup(id)
        override suspend fun insert(identity: IdentityEntity): Long { writes++; return 1 }
        override suspend fun update(identity: IdentityEntity) { writes++ }
        override suspend fun delete(identity: IdentityEntity) { error("Unexpected delete") }
        override suspend fun deleteIfUnused(id: Long): Int = error("Unexpected delete")
        override suspend fun countUsages(id: Long) = 0
    }
}
