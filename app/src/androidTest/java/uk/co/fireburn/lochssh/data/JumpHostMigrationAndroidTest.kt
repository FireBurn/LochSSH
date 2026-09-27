package uk.co.fireburn.lochssh.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uk.co.fireburn.lochssh.data.db.LochSshDatabase

class JumpHostMigrationAndroidTest {
    @Test
    fun migratesExistingHostsAndStoresJumpChoice() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "jump_migration_${UUID.randomUUID()}.db"
        var database: LochSshDatabase? = null
        try {
            context.openOrCreateDatabase(name, 0, null).use { old ->
                old.execSQL("CREATE TABLE ssh_hosts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, host TEXT NOT NULL, port INTEGER NOT NULL, identity_id INTEGER, keepAliveSeconds INTEGER NOT NULL, `group` TEXT NOT NULL, auto_command TEXT NOT NULL DEFAULT '', remote_session_mode TEXT NOT NULL DEFAULT 'SHELL')")
                old.execSQL("CREATE TABLE identities (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, username TEXT NOT NULL, authType TEXT NOT NULL, keyPath TEXT, key_material_ref TEXT, secret_ref TEXT)")
                old.execSQL("CREATE TABLE port_forwards (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, host_id INTEGER NOT NULL, type TEXT NOT NULL, localPort INTEGER NOT NULL, remoteHost TEXT NOT NULL, remotePort INTEGER NOT NULL)")
                old.execSQL("CREATE TABLE saved_commands (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, command TEXT NOT NULL)")
                old.execSQL("CREATE TABLE connection_history (token TEXT NOT NULL PRIMARY KEY, host_id INTEGER NOT NULL, name TEXT NOT NULL, host TEXT NOT NULL, port INTEGER NOT NULL, started_at INTEGER NOT NULL, ended_at INTEGER, status TEXT NOT NULL)")
                old.execSQL("INSERT INTO identities VALUES(1, 'test', 'tester', 'NONE', NULL, NULL, NULL)")
                old.execSQL("INSERT INTO ssh_hosts VALUES(1, 'destination', 'example.test', 2222, 1, 30, 'group', 'pwd', 'SHELL')")
                old.execSQL("INSERT INTO port_forwards VALUES(1, 1, 'LOCAL', 8080, 'localhost', 80)")
                old.version = 6
            }
            val migrated = Room.databaseBuilder(context, LochSshDatabase::class.java, name)
                .addMigrations(LochSshDatabase.MIGRATION_6_7).build()
            database = migrated
            val host = requireNotNull(migrated.sshHostDao().getById(1))
            assertNull(host.jumpHostId)
            assertEquals("pwd", host.autoCommand)
            assertEquals(2222, host.port)
            assertEquals("tester", migrated.identityDao().getById(1)?.username)
            assertEquals(8080, migrated.portForwardDao().getByHost(1).single().localPort)
            val jump = migrated.sshHostDao().insert(host.copy(id = 0, name = "jump"))
            migrated.sshHostDao().update(host.copy(jumpHostId = jump))
            assertEquals(jump, migrated.sshHostDao().getById(1)?.jumpHostId)
            assertEquals(1, migrated.sshHostDao().countUsingJump(jump))
        } finally {
            database?.close()
            context.deleteDatabase(name)
        }
    }
}
