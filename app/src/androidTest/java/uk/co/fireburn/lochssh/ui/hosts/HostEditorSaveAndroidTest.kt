package uk.co.fireburn.lochssh.ui.hosts

import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.fireburn.lochssh.data.db.ForwardTypes
import uk.co.fireburn.lochssh.data.db.LochSshDatabase
import uk.co.fireburn.lochssh.data.db.PortForwardEntity
import uk.co.fireburn.lochssh.data.db.RemoteSessionModes
import uk.co.fireburn.lochssh.data.db.SshHostEntity

class HostEditorSaveAndroidTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val database = Room.inMemoryDatabaseBuilder(
        instrumentation.targetContext, LochSshDatabase::class.java).build()
    private val store = ViewModelStore()

    @After
    fun close() {
        instrumentation.runOnMainSync { store.clear() }
        database.close()
    }

    @Test
    fun savesReloadsAndDisablesPortKnocking() = runBlocking {
        val id = database.sshHostDao().insert(SshHostEntity(name = "Test", host = "test.invalid"))
        val editor = openEditor(id, "first")
        assertNull(saveKnocking(editor, "7000,8000:udp", "150"))
        val reopened = openEditor(id, "reopened")
        instrumentation.runOnMainSync {
            assertEquals("7000,8000:udp", reopened.host?.knockSequence)
            assertEquals(150L, reopened.host?.knockPause)
        }
        assertEquals("Ports must be between 1 and 65535", saveKnocking(reopened, "65536", "150"))
        assertEquals("7000,8000:udp", database.sshHostDao().getById(id)?.knockSequence)
        assertNull(saveKnocking(reopened, "", "invalid"))
        assertEquals("", database.sshHostDao().getById(id)?.knockSequence)
        assertEquals(200L, database.sshHostDao().getById(id)?.knockPause)
    }

    private suspend fun openEditor(id: Long, key: String): HostEditorViewModel {
        lateinit var editor: HostEditorViewModel
        instrumentation.runOnMainSync {
            editor = HostEditorViewModel(database.sshHostDao(), database.identityDao(), database.portForwardDao(), database)
            store.put(key, editor)
            editor.load(id)
        }
        withTimeout(5000) {
            var loaded = false
            while (!loaded) {
                instrumentation.runOnMainSync { loaded = editor.loaded }
                if (!loaded) delay(10)
            }
        }
        return editor
    }

    private suspend fun saveKnocking(editor: HostEditorViewModel, sequence: String, pause: String): String? {
        val finished = CompletableDeferred<String?>()
        instrumentation.runOnMainSync {
            editor.save("Test", "test.invalid", "22", "30", "", null, "",
                RemoteSessionModes.SHELL, null, false, sequence, pause) { finished.complete(it) }
        }
        return withTimeout(5000) { finished.await() }
    }

    @Test
    fun savingARemovedHostDoesNotRecreateItsTunnels() = runBlocking {
        val hosts = database.sshHostDao()
        val tunnels = database.portForwardDao()
        val id = hosts.insert(SshHostEntity(name = "Test", host = "test.invalid"))
        tunnels.insert(PortForwardEntity(hostId = id, type = ForwardTypes.LOCAL,
            localPort = 8080, remoteHost = "localhost", remotePort = 80))
        lateinit var editor: HostEditorViewModel
        instrumentation.runOnMainSync {
            editor = HostEditorViewModel(hosts, database.identityDao(), tunnels, database)
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
        instrumentation.runOnMainSync { assertEquals(1, editor.forwards.size) }
        database.withTransaction {
            tunnels.deleteByHost(id)
            hosts.delete(requireNotNull(hosts.getById(id)))
        }
        val finished = CompletableDeferred<String?>()
        instrumentation.runOnMainSync {
            editor.save("Changed", "changed.invalid", "22", "30", "", null,
                "", RemoteSessionModes.SHELL, null, false) { finished.complete(it) }
        }
        assertEquals("This host no longer exists", withTimeout(5_000) { finished.await() })
        assertNull(hosts.getById(id))
        assertTrue(tunnels.getByHost(id).isEmpty())
    }
}
