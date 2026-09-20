package uk.co.fireburn.lochssh.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

object ForwardTypes {
    const val LOCAL = "LOCAL"
    const val REMOTE = "REMOTE"
}

@Entity(tableName = "port_forwards")
data class PortForwardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "host_id") val hostId: Long,
    val type: String,
    val localPort: Int,
    val remoteHost: String,
    val remotePort: Int
)
