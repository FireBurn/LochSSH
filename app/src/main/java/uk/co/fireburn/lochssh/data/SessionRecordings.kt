package uk.co.fireburn.lochssh.data

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import uk.co.fireburn.lochssh.ssh.RecordingCoordinator
import uk.co.fireburn.lochssh.ssh.RecordingFiles
import uk.co.fireburn.lochssh.ssh.SshConnectionManager

@Singleton
class SessionRecordings @Inject constructor(@ApplicationContext private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val files = RecordingFiles(File(context.noBackupFilesDir, "terminal-recordings"))
    private val coordinator = RecordingCoordinator(files, scope)
    internal val recordings = coordinator.recordings

    internal suspend fun start(manager: SshConnectionManager, hostLabel: String) = withContext(Dispatchers.IO) {
        check(manager.isConnected) { "Connection is closed" }
        coordinator.start(manager.recording, hostLabel)
    }

    internal suspend fun list() = withContext(Dispatchers.IO) { files.list() }

    suspend fun clear() = withContext(Dispatchers.IO) { coordinator.clear() }

    suspend fun clearThen(block: suspend () -> Unit) = withContext(Dispatchers.IO) {
        coordinator.clear(block)
    }

    internal suspend fun delete(entry: RecordingFiles.Entry) = withContext(Dispatchers.IO) {
        files.delete(entry)
    }

    internal suspend fun export(entry: RecordingFiles.Entry, destination: Uri) = withContext(Dispatchers.IO) {
        val output = context.contentResolver.openOutputStream(destination, "wt")
            ?: throw IOException("Could not open recording export")
        output.use { files.export(entry, it) }
    }
}
