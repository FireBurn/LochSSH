package uk.co.fireburn.lochssh.ssh

// In-process bridge so the UI can reach the live connection owned by the service.
object ActiveConnection {
    @Volatile
    var manager: SshConnectionManager? = null

    @Volatile
    var lastError: String? = null
}
