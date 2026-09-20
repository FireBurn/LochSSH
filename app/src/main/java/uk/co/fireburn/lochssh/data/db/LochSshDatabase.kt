package uk.co.fireburn.lochssh.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SshHostEntity::class,
        IdentityEntity::class,
        PortForwardEntity::class
    ],
    version = 1
)
abstract class LochSshDatabase : RoomDatabase() {
    abstract fun sshHostDao(): SshHostDao
    abstract fun identityDao(): IdentityDao
    abstract fun portForwardDao(): PortForwardDao

    companion object {
        const val NAME = "lochssh.db"
    }
}
