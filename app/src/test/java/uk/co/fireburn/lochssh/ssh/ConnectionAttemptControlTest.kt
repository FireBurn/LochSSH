package uk.co.fireburn.lochssh.ssh

import java.io.ByteArrayInputStream
import java.io.InterruptedIOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionAttemptControlTest {
    @Test(expected = InterruptedIOException::class)
    fun rejectsCancelledAttemptBeforeStarting() {
        val control = ConnectionAttemptControl()
        control.cancel()
        control.run { error("Cancelled attempt started") }
    }

    @Test
    fun interruptsSilentChannelWithoutWaitingForTimeout() {
        val control = ConnectionAttemptControl()
        val started = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        val worker = thread(isDaemon = true) {
            try {
                control.run {
                    started.countDown()
                    TimedChannelInput(ByteArrayInputStream(byteArrayOf())) { true }.read()
                }
            } catch (error: Throwable) { failure.set(error) }
            finally { finished.countDown() }
        }
        try {
            assertTrue(started.await(2, TimeUnit.SECONDS))
            control.cancel()
            assertTrue(finished.await(2, TimeUnit.SECONDS))
            assertTrue(failure.get() is InterruptedIOException)
        } finally {
            control.cancel()
            worker.join(2000)
        }
        assertFalse(worker.isAlive)
    }

    @Test(expected = InterruptedIOException::class)
    fun rejectsCompletionAfterCancellation() {
        val control = ConnectionAttemptControl()
        control.run { control.cancel() }
    }
}
