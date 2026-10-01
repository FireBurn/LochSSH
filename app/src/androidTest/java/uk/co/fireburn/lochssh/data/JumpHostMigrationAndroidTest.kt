package uk.co.fireburn.lochssh.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.fireburn.lochssh.data.db.LochSshDatabase

class JumpHostMigrationAndroidTest {
    @Test
    fun migratesExistingHostsAndStoresJumpChoice() = migrate(6)

    @Test
    fun preservesExistingJumpWhenAddingAgentSetting() = migrate(7)

    @Test
    fun preservesExistingRowsWhenAddingSecretCleanup() = migrate(8)

    private fun migrate(startVersion: Int) = runBlocking {
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
                if (startVersion >= 7) {
                    old.execSQL("ALTER TABLE ssh_hosts ADD COLUMN jump_host_id INTEGER DEFAULT NULL")
                    old.execSQL("INSERT INTO ssh_hosts VALUES(2, 'jump', 'jump.test', 22, 1, 30, '', '', 'SHELL', NULL)")
                    old.execSQL("UPDATE ssh_hosts SET jump_host_id = 2 WHERE id = 1")
                }
                if (startVersion >= 8) old.execSQL("ALTER TABLE ssh_hosts ADD COLUMN agent_forwarding INTEGER NOT NULL DEFAULT 0")
                old.version = startVersion
            }
            val migrated = Room.databaseBuilder(context, LochSshDatabase::class.java, name)
                .addMigrations(LochSshDatabase.MIGRATION_6_7, LochSshDatabase.MIGRATION_7_8,
                    LochSshDatabase.MIGRATION_8_9).build()
            database = migrated
            val host = requireNotNull(migrated.sshHostDao().getById(1))
            if (startVersion == 6) assertNull(host.jumpHostId) else assertEquals(2L, host.jumpHostId)
            assertFalse(host.agentForwarding)
            assertTrue(migrated.secretCleanupDao().pending().isEmpty())
            assertEquals("pwd", host.autoCommand)
            assertEquals(2222, host.port)
            assertEquals("tester", migrated.identityDao().getById(1)?.username)
            assertEquals(8080, migrated.portForwardDao().getByHost(1).single().localPort)
            val jump = migrated.sshHostDao().insert(host.copy(id = 0, name = "jump"))
            migrated.sshHostDao().update(host.copy(jumpHostId = jump, agentForwarding = true))
            assertEquals(jump, migrated.sshHostDao().getById(1)?.jumpHostId)
            assertEquals(1, migrated.sshHostDao().countUsingJump(jump))
            assertTrue(requireNotNull(migrated.sshHostDao().getById(1)).agentForwarding)
        } finally {
            database?.close()
            context.deleteDatabase(name)
        }
    }
}
