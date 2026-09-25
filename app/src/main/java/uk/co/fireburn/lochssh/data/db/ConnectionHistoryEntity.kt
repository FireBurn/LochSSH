package uk.co.fireburn.lochssh.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

object ConnectionStates {
    const val CONNECTING = "CONNECTING"
    const val CONNECTED = "CONNECTED"
    const val ENDED = "ENDED"
    const val FAILED = "FAILED"
    const val CANCELLED = "CANCELLED"
    const val INTERRUPTED = "INTERRUPTED"
}

@Entity(tableName = "connection_history")
data class ConnectionHistoryEntity(
    @PrimaryKey val token: String,
    @ColumnInfo(name = "host_id") val hostId: Long,
    val name: String,
    val host: String,
    val port: Int,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long? = null,
    val status: String = ConnectionStates.CONNECTING
)
