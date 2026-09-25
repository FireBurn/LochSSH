package uk.co.fireburn.lochssh.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

object AuthTypes {
    const val PASSWORD = "PASSWORD"
    const val PUBLIC_KEY = "PUBLIC_KEY"
    const val NONE = "NONE"
}

object RemoteSessionModes {
    const val SHELL = "SHELL"
    const val ASK = "ASK"
    const val TMUX = "TMUX"
    const val SCREEN = "SCREEN"
    val ALL = listOf(SHELL, ASK, TMUX, SCREEN)
}

@Entity(tableName = "ssh_hosts")
data class SshHostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int = 22,
    @ColumnInfo(name = "identity_id") val identityId: Long? = null,
    val keepAliveSeconds: Int = 30,
    val group: String = "",
    @ColumnInfo(name = "auto_command", defaultValue = "''") val autoCommand: String = "",
    @ColumnInfo(name = "remote_session_mode", defaultValue = "'SHELL'")
    val remoteSessionMode: String = RemoteSessionModes.SHELL
)
