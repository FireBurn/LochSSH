package uk.co.fireburn.lochssh.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedCommandDao {
    @Query("SELECT * FROM saved_commands ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<SavedCommandEntity>>

    @Insert
    suspend fun insert(command: SavedCommandEntity)

    @Update
    suspend fun update(command: SavedCommandEntity)

    @Delete
    suspend fun delete(command: SavedCommandEntity)
}
