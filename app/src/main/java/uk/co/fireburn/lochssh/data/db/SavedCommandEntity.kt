package uk.co.fireburn.lochssh.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_commands")
data class SavedCommandEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val command: String
)
