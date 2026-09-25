package uk.co.fireburn.lochssh.data

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.fireburn.lochssh.data.db.ConnectionHistoryDao
import uk.co.fireburn.lochssh.data.db.ConnectionHistoryEntity
import uk.co.fireburn.lochssh.data.db.ConnectionStates

class ConnectionHistoryRecorderTest {
    @Test
    fun recordsEventsInOrder() {
        val dao = RecordingDao()
        val recorder = ConnectionHistoryRecorder(dao)
        recorder.start()
        recorder.started("attempt", 7, 100)
        recorder.host("attempt", "Test host", "example.test", 22)
        recorder.connected("attempt")
        recorder.finished("attempt", ConnectionStates.ENDED, 200)

        assertTrue(dao.done.await(5, TimeUnit.SECONDS))
        assertEquals(
            listOf("recover", "start:attempt", "host:attempt", "connected:attempt", "ENDED:attempt"),
            dao.events.toList()
        )
    }

    @Test
    fun clearWaitsForEarlierEvents() = runBlocking {
        val dao = RecordingDao()
        val recorder = ConnectionHistoryRecorder(dao)
        recorder.start()
        recorder.started("attempt", 7, 100)

        recorder.clear()

        assertTrue(dao.events.isEmpty())
    }

    private class RecordingDao : ConnectionHistoryDao {
        val events = Collections.synchronizedList(mutableListOf<String>())
        val done = CountDownLatch(1)

        override fun observeRecent(): Flow<List<ConnectionHistoryEntity>> = emptyFlow()

        override suspend fun insert(entry: ConnectionHistoryEntity) {
            events += "start:${entry.token}"
        }

        override suspend fun setHost(token: String, name: String, host: String, port: Int) {
            events += "host:$token"
        }

        override suspend fun markConnected(token: String) {
            events += "connected:$token"
        }

        override suspend fun finish(token: String, status: String, endedAt: Long) {
            events += "$status:$token"
            done.countDown()
        }

        override suspend fun markInterrupted(endedAt: Long) {
            events += "recover"
        }

        override suspend fun clear() {
            events.clear()
        }
    }
}
