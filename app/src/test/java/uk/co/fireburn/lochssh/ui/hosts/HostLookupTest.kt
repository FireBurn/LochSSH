package uk.co.fireburn.lochssh.ui.hosts

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import uk.co.fireburn.lochssh.data.db.ForwardTypes
import uk.co.fireburn.lochssh.data.db.PortForwardEntity
import uk.co.fireburn.lochssh.data.db.SshHostEntity

class HostLookupTest {
    @Test
    fun newHostDoesNotReadSavedRows() = runBlocking {
        val result = HostLookup.load(0) { error("Unexpected read") }
        assertNull(result.details)
        assertNull(result.error)
    }

    @Test
    fun retainsTheHostAndItsTunnelsTogether() = runBlocking {
        val details = HostDetails(SshHostEntity(7, "Test", "test.invalid"),
            listOf(PortForwardEntity(3, 7, ForwardTypes.LOCAL, 8080, "localhost", 80)))
        val result = HostLookup.load(7) { assertEquals(7L, it); details }
        assertEquals(details, result.details)
        assertNull(result.error)
    }

    @Test
    fun missingHostIsNotANewHost() = runBlocking {
        val result = HostLookup.load(7) { null }
        assertNull(result.details)
        assertEquals("This host no longer exists", result.error)
    }

    @Test
    fun failedReadDoesNotKeepPartialDetails() = runBlocking {
        val result = HostLookup.load(7) { throw IllegalStateException("tunnel read failed") }
        assertNull(result.details)
        assertEquals("Could not load host: tunnel read failed", result.error)
    }

    @Test
    fun rejectsInvalidIdsWithoutReadingRows() = runBlocking {
        assertEquals("Invalid host", HostLookup.load(-1) { error("Unexpected read") }.error)
    }

    @Test
    fun preservesCancellation() {
        assertThrows(CancellationException::class.java) {
            runBlocking { HostLookup.load(7) { throw CancellationException() } }
        }
    }
}
