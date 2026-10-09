package uk.co.fireburn.lochssh.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SshHostDao {
    @Query("SELECT * FROM ssh_hosts ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<SshHostEntity>>

    @Query("SELECT * FROM ssh_hosts WHERE id = :id")
    suspend fun getById(id: Long): SshHostEntity?
    @Query("SELECT COUNT(*) FROM ssh_hosts WHERE jump_host_id = :id")
    suspend fun countUsingJump(id: Long): Int

    @Insert
    suspend fun insert(host: SshHostEntity): Long

    @Query("UPDATE ssh_hosts SET tmux_seen = :seen WHERE id = :id")
    suspend fun setTmuxSeen(id: Long, seen: Boolean)
    @Query("UPDATE ssh_hosts SET screen_seen = :seen WHERE id = :id")
    suspend fun setScreenSeen(id: Long, seen: Boolean)

    @Update
    suspend fun update(host: SshHostEntity): Int

    @Delete
    suspend fun delete(host: SshHostEntity)
}
