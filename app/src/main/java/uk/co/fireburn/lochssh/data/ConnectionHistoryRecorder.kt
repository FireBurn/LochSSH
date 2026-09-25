package uk.co.fireburn.lochssh.data

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import uk.co.fireburn.lochssh.data.db.ConnectionHistoryDao
import uk.co.fireburn.lochssh.data.db.ConnectionHistoryEntity

@Singleton
class ConnectionHistoryRecorder @Inject constructor(
    private val dao: ConnectionHistoryDao
) {
    private sealed interface Event {
        data class Started(val entry: ConnectionHistoryEntity) : Event
        data class Host(val token: String, val name: String, val address: String, val port: Int) : Event
        data class Connected(val token: String) : Event
        data class Finished(val token: String, val status: String, val time: Long) : Event
        data class Clear(val done: CompletableDeferred<Unit>) : Event
    }

    private val started = AtomicBoolean(false)
    private val events = Channel<Event>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            runCatching { dao.markInterrupted(System.currentTimeMillis()) }
                .onFailure { Log.e(TAG, "Could not close old connection history", it) }
            for (event in events) {
                runCatching {
                    when (event) {
                        is Event.Started -> dao.insert(event.entry)
                        is Event.Host -> dao.setHost(event.token, event.name, event.address, event.port)
                        is Event.Connected -> dao.markConnected(event.token)
                        is Event.Finished -> dao.finish(event.token, event.status, event.time)
                        is Event.Clear -> {
                            dao.clear()
                            event.done.complete(Unit)
                        }
                    }
                }.onFailure {
                    if (event is Event.Clear) event.done.completeExceptionally(it)
                    Log.e(TAG, "Could not save connection history", it)
                }
            }
        }
    }

    fun started(token: String, hostId: Long, time: Long = System.currentTimeMillis()) {
        events.trySend(Event.Started(ConnectionHistoryEntity(token, hostId, "Host", "", 0, time)))
    }

    fun host(token: String, name: String, address: String, port: Int) {
        events.trySend(Event.Host(token, name, address, port))
    }

    fun connected(token: String) {
        events.trySend(Event.Connected(token))
    }

    fun finished(token: String, status: String, time: Long = System.currentTimeMillis()) {
        events.trySend(Event.Finished(token, status, time))
    }

    suspend fun clear() {
        start()
        val done = CompletableDeferred<Unit>()
        events.send(Event.Clear(done))
        done.await()
    }

    companion object {
        private const val TAG = "LochSSH-history"
    }
}
