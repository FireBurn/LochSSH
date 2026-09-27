package uk.co.fireburn.lochssh.data

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import uk.co.fireburn.lochssh.data.db.SavedCommandDao
import uk.co.fireburn.lochssh.data.db.SavedCommandEntity

class SavedCommandStoreTest {
    private class Dao : SavedCommandDao {
        var failure: Exception? = null
        var saved: SavedCommandEntity? = null
        var deleted: SavedCommandEntity? = null
        override fun observeAll() = flowOf(emptyList<SavedCommandEntity>())
        override suspend fun insert(command: SavedCommandEntity) {
            failure?.let { throw it }
            saved = command
        }
        override suspend fun update(command: SavedCommandEntity) = insert(command)
        override suspend fun delete(command: SavedCommandEntity) {
            failure?.let { throw it }
            deleted = command
        }
    }

    @Test
    fun savesEditsAndDeletes() = runBlocking {
        val dao = Dao()
        val store = SavedCommandStore(dao)
        assertNull(store.save(0, " List ", " ls "))
        assertEquals(SavedCommandEntity(0, "List", "ls"), dao.saved)
        assertNull(store.save(4, "List", "ls -a"))
        assertEquals(4L, dao.saved?.id)
        assertNull(store.delete(requireNotNull(dao.saved)))
        assertEquals(dao.saved, dao.deleted)
    }

    @Test
    fun rejectsMultilineBeforeTrimming() = runBlocking {
        val dao = Dao()
        assertNotNull(SavedCommandStore(dao).save(0, "List", "ls\n"))
        assertNull(dao.saved)
    }

    @Test
    fun reportsStorageFailures() = runBlocking {
        val dao = Dao().apply { failure = IOException("disk full") }
        val store = SavedCommandStore(dao)
        assertEquals("Could not save command: disk full", store.save(0, "List", "ls"))
        assertEquals("Could not delete command: disk full", store.delete(SavedCommandEntity(1, "List", "ls")))
    }

    @Test(expected = CancellationException::class)
    fun preservesCancellation() { runBlocking {
        val dao = Dao().apply { failure = CancellationException("cancelled") }
        SavedCommandStore(dao).save(0, "List", "ls")
    } }
}
