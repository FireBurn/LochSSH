package uk.co.fireburn.lochssh.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SshHostEntity::class,
        IdentityEntity::class,
        PortForwardEntity::class
    ],
    version = 4
)
abstract class LochSshDatabase : RoomDatabase() {
    abstract fun sshHostDao(): SshHostDao
    abstract fun identityDao(): IdentityDao
    abstract fun portForwardDao(): PortForwardDao

    companion object {
        const val NAME = "lochssh.db"

        // Username moved from hosts to identities; key material added.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE identities ADD COLUMN username TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE identities ADD COLUMN key_material_ref TEXT")
                db.execSQL(
                    "UPDATE identities SET username = COALESCE(" +
                        "(SELECT username FROM ssh_hosts WHERE ssh_hosts.identity_id = identities.id LIMIT 1), '')"
                )
                // Room validates the full column set, so rebuild ssh_hosts without username.
                // DROP COLUMN needs SQLite 3.35+, unavailable on minSdk 26.
                db.execSQL(
                    "CREATE TABLE `ssh_hosts_new` (`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                        "`name` TEXT NOT NULL, `host` TEXT NOT NULL, `port` INTEGER NOT NULL, " +
                        "`identity_id` INTEGER, `keepAliveSeconds` INTEGER NOT NULL, " +
                        "`group` TEXT NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO `ssh_hosts_new` (`id`, `name`, `host`, `port`, `identity_id`, " +
                        "`keepAliveSeconds`, `group`) SELECT `id`, `name`, `host`, `port`, " +
                        "`identity_id`, `keepAliveSeconds`, `group` FROM `ssh_hosts`"
                )
                db.execSQL("DROP TABLE `ssh_hosts`")
                db.execSQL("ALTER TABLE `ssh_hosts_new` RENAME TO `ssh_hosts`")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ssh_hosts ADD COLUMN auto_command TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ssh_hosts ADD COLUMN remote_session_mode TEXT NOT NULL DEFAULT 'SHELL'")
            }
        }
    }
}
