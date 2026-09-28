package uk.co.fireburn.lochssh.ssh

import java.io.ByteArrayInputStream
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class TimedChannelInputTest {
    @Test
    fun readsBufferedDataBeforeClosedChannelEnd() {
        val input = TimedChannelInput(ByteArrayInputStream(byteArrayOf(1, 2, 3))) { false }
        assertEquals(1, input.read())
        val bytes = ByteArray(2)
        assertEquals(2, input.read(bytes))
        assertArrayEquals(byteArrayOf(2, 3), bytes)
        assertEquals(-1, input.read())
        assertEquals(0, input.read(bytes, 0, 0))
    }

    @Test(expected = SocketTimeoutException::class)
    fun timesOutWhenConnectedButSilent() {
        TimedChannelInput(ByteArrayInputStream(byteArrayOf())) { true }.apply { timeoutMillis = 20 }.read()
    }

    @Test(expected = InterruptedIOException::class)
    fun cancelsWaitingRead() {
        Thread.currentThread().interrupt()
        try {
            TimedChannelInput(ByteArrayInputStream(byteArrayOf())) { true }.read()
        } finally { Thread.interrupted() }
    }
}
