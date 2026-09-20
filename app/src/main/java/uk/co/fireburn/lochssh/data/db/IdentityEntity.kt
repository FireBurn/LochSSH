package uk.co.fireburn.lochssh.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "identities")
data class IdentityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val authType: String,
    val keyPath: String? = null,
    // Reference into EncryptedStorageManager, never the secret itself.
    @ColumnInfo(name = "secret_ref") val secretRef: String? = null
)
