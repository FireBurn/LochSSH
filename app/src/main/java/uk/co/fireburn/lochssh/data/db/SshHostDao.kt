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

    @Insert
    suspend fun insert(host: SshHostEntity): Long

    @Update
    suspend fun update(host: SshHostEntity)

    @Delete
    suspend fun delete(host: SshHostEntity)
}
