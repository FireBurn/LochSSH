package uk.co.fireburn.lochssh.ssh

import org.junit.Assert.assertEquals
import org.junit.Test

class ForwardWarningsTest {
    @Test
    fun remainingTerminalReceivesErrorsAfterTheFirstCloses() {
        val warnings = ForwardWarnings()
        val first = mutableListOf<String>()
        val second = mutableListOf<String>()
        val firstSubscription = warnings.listen(first::add)
        val secondSubscription = warnings.listen(second::add)
        warnings.report("Port could not be opened")

        firstSubscription.close()
        warnings.report("Proxy connection failed")

        assertEquals(listOf("Port could not be opened"), first)
        assertEquals(listOf("Port could not be opened", "Proxy connection failed"), second)
        secondSubscription.close()
    }

    @Test
    fun newTerminalReceivesTheLastFiveDifferentWarnings() {
        val warnings = ForwardWarnings()
        repeat(7) { warnings.report("Port $it could not be opened") }
        warnings.report("Port 6 could not be opened")
        val received = mutableListOf<String>()
        val subscription = warnings.listen(received::add)

        assertEquals((2..6).map { "Port $it could not be opened" }, received)
        subscription.close()
        subscription.close()
        warnings.report("Later error")
        assertEquals(5, received.size)
    }

    @Test
    fun repeatedFailuresStillReachTerminalsThatMayHaveDismissedTheWarning() {
        val warnings = ForwardWarnings()
        val current = mutableListOf<String>()
        val later = mutableListOf<String>()
        warnings.listen(current::add).use {
            warnings.report("Proxy connection failed")
            warnings.report("Proxy connection failed")
            warnings.listen(later::add).close()
        }

        assertEquals(listOf("Proxy connection failed", "Proxy connection failed"), current)
        assertEquals(listOf("Proxy connection failed"), later)
    }
}
