package uk.co.fireburn.lochssh.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "secret_cleanup")
data class SecretCleanupEntity(@PrimaryKey val reference: String)
