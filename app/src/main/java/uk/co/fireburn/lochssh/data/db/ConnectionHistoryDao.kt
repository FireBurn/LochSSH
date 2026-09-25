package uk.co.fireburn.lochssh.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionHistoryDao {
    @Query("SELECT * FROM connection_history ORDER BY started_at DESC LIMIT 200")
    fun observeRecent(): Flow<List<ConnectionHistoryEntity>>

    @Insert
    suspend fun insert(entry: ConnectionHistoryEntity)

    @Query("UPDATE connection_history SET name = :name, host = :host, port = :port WHERE token = :token")
    suspend fun setHost(token: String, name: String, host: String, port: Int)

    @Query("UPDATE connection_history SET status = 'CONNECTED' WHERE token = :token AND status = 'CONNECTING'")
    suspend fun markConnected(token: String)

    @Query("UPDATE connection_history SET status = :status, ended_at = :endedAt " +
        "WHERE token = :token AND status IN ('CONNECTING', 'CONNECTED')")
    suspend fun finish(token: String, status: String, endedAt: Long)

    @Query("UPDATE connection_history SET status = 'INTERRUPTED', ended_at = :endedAt " +
        "WHERE status IN ('CONNECTING', 'CONNECTED')")
    suspend fun markInterrupted(endedAt: Long)

    @Query("DELETE FROM connection_history")
    suspend fun clear()
}
