package uk.co.fireburn.lochssh.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SecretCleanupDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(entry: SecretCleanupEntity)

    @Query("SELECT reference FROM secret_cleanup ORDER BY reference")
    suspend fun pending(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM identities WHERE secret_ref = :reference OR key_material_ref = :reference)")
    suspend fun isUsed(reference: String): Boolean

    @Query("DELETE FROM secret_cleanup WHERE reference = :reference")
    suspend fun remove(reference: String)
}
