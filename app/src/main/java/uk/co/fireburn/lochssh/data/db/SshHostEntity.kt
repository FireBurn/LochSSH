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
    const val CONNECT_ALL = "CONNECT_ALL"
    const val AUTO = "AUTO"
    val ALL = listOf(SHELL, ASK, TMUX, SCREEN, CONNECT_ALL, AUTO)
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
    val remoteSessionMode: String = RemoteSessionModes.SHELL,
    @ColumnInfo(name = "jump_host_id", defaultValue = "NULL") val jumpHostId: Long? = null,
    @ColumnInfo(name = "agent_forwarding", defaultValue = "0") val agentForwarding: Boolean = false,
    @ColumnInfo(name = "knock_sequence", defaultValue = "''") val knockSequence: String = "",
    @ColumnInfo(name = "knock_pause", defaultValue = "200") val knockPause: Long = 200,
    @ColumnInfo(name = "proxy_type", defaultValue = "'NONE'") val proxyType: String = "NONE",
    @ColumnInfo(name = "proxy_host", defaultValue = "''") val proxyHost: String = "",
    @ColumnInfo(name = "proxy_port", defaultValue = "1080") val proxyPort: Int = 1080,
    @ColumnInfo(name = "proxy_identity_id", defaultValue = "NULL") val proxyIdentityId: Long? = null,
    @ColumnInfo(name = "telnet", defaultValue = "0") val telnet: Boolean = false,
    @ColumnInfo(name = "local_shell", defaultValue = "0") val localShell: Boolean = false,
    @ColumnInfo(name = "tmux_seen", defaultValue = "0") val tmuxSeen: Boolean = false,
    @ColumnInfo(name = "screen_seen", defaultValue = "0") val screenSeen: Boolean = false
)
