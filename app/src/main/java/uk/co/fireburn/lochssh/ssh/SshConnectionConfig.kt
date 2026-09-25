package uk.co.fireburn.lochssh.ssh

import uk.co.fireburn.lochssh.data.db.RemoteSessionModes

data class PortForwardSpec(
    val type: String,
    val localPort: Int,
    val remoteHost: String,
    val remotePort: Int
)

data class SshConnectionConfig(
    val host: String,
    val port: Int,
    val username: String,
    val authType: String,
    val password: String?,
    val keyPath: String?,
    val keyMaterial: String?,
    val keyPassphrase: String?,
    val keepAliveSeconds: Int,
    val autoCommand: String = "",
    val remoteSessionMode: String = RemoteSessionModes.SHELL,
    val initialCols: Int = 80,
    val initialRows: Int = 24,
    val forwards: List<PortForwardSpec> = emptyList()
)
