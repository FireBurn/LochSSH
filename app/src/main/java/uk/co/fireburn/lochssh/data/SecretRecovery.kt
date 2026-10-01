package uk.co.fireburn.lochssh.data

import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.fireburn.lochssh.data.db.LochSshDatabase

@Singleton
class SecretRecovery @Inject constructor(
    private val database: LochSshDatabase,
    private val secrets: EncryptedStorageManager
) {
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _running = MutableStateFlow(false)
    val running = _running.asStateFlow()

    fun start() {
        if (started.compareAndSet(false, true)) scope.launch { retry() }
    }

    suspend fun retry() = withContext(Dispatchers.IO) {
        secrets.change {
            _running.value = true
            try {
                val cleanup = database.secretCleanupDao()
                val remaining = SecretCleanup.retry(cleanup.pending(), cleanup::isUsed,
                    secrets::deleteSecret, cleanup::remove)
                _error.value = if (remaining.isEmpty()) null
                else "Could not remove all unused stored secrets. Try again."
            } catch (_: CancellationException) {
                currentCoroutineContext().ensureActive()
                _error.value = "Could not check stored secret cleanup. Try again."
            } catch (_: Exception) {
                _error.value = "Could not check stored secret cleanup. Try again."
            } finally {
                _running.value = false
            }
        }
    }

    fun clearError() { _error.value = null }
}
