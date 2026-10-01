package uk.co.fireburn.lochssh.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class ClearDataDao {
    @Transaction
    open suspend fun clearSavedData() {
        queueSecrets()
        clearForwards()
        clearHosts()
        clearIdentities()
        clearCommands()
        clearHistory()
    }

    @Query("INSERT OR IGNORE INTO secret_cleanup (reference) SELECT secret_ref FROM identities WHERE secret_ref IS NOT NULL UNION SELECT key_material_ref FROM identities WHERE key_material_ref IS NOT NULL")
    abstract suspend fun queueSecrets()

    @Query("DELETE FROM port_forwards")
    abstract suspend fun clearForwards()

    @Query("DELETE FROM ssh_hosts")
    abstract suspend fun clearHosts()

    @Query("DELETE FROM identities")
    abstract suspend fun clearIdentities()

    @Query("DELETE FROM saved_commands")
    abstract suspend fun clearCommands()

    @Query("DELETE FROM connection_history")
    abstract suspend fun clearHistory()

    @Query("DELETE FROM secret_cleanup")
    abstract suspend fun clearCleanup()
}
