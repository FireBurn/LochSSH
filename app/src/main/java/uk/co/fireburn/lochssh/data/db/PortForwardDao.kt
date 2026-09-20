package uk.co.fireburn.lochssh.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PortForwardDao {
    @Query("SELECT * FROM port_forwards WHERE host_id = :hostId")
    fun observeByHost(hostId: Long): Flow<List<PortForwardEntity>>

    @Query("SELECT * FROM port_forwards WHERE id = :id")
    suspend fun getById(id: Long): PortForwardEntity?

    @Insert
    suspend fun insert(forward: PortForwardEntity): Long

    @Update
    suspend fun update(forward: PortForwardEntity)

    @Delete
    suspend fun delete(forward: PortForwardEntity)

    @Query("DELETE FROM port_forwards WHERE host_id = :hostId")
    suspend fun deleteByHost(hostId: Long)
}
