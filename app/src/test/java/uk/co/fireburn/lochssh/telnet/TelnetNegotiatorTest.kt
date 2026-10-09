package uk.co.fireburn.lochssh.telnet

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class TelnetNegotiatorTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
    private val iac = 255

    @Test
    fun plainTextPassesThroughAndDoubledIacIsOneByte() {
        val result = TelnetNegotiator().receive(bytes('h'.code, iac, iac, 'i'.code))
        assertArrayEquals(bytes('h'.code, 255, 'i'.code), result.text)
        assertEquals(0, result.replies.size)
    }

    @Test
    fun acceptsEchoAndSuppressGoAheadAndRefusesOthersOnce() {
        val negotiator = TelnetNegotiator()
        val result = negotiator.receive(bytes(iac, 251, 1, iac, 251, 3, iac, 251, 9, iac, 251, 1))
        assertArrayEquals(bytes(iac, 253, 1, iac, 253, 3, iac, 254, 9), result.replies)
        assertEquals(0, result.text.size)
    }

    @Test
    fun windowSizeIsSentOnAcceptanceAndOnResize() {
        val negotiator = TelnetNegotiator()
        negotiator.windowSize(100, 40)
        val result = negotiator.receive(bytes(iac, 253, 31))
        assertArrayEquals(bytes(iac, 251, 31, iac, 250, 31, 0, 100, 0, 40, iac, 240), result.replies)
        // 0x01FF columns: the 0xFF byte is doubled
        assertArrayEquals(bytes(iac, 250, 31, 1, 255, 255, 0, 5, iac, 240), negotiator.windowSize(0x1FF, 5))
    }

    @Test
    fun terminalTypeIsReportedWhenAskedAfterAcceptingIt() {
        val negotiator = TelnetNegotiator("vt100")
        negotiator.receive(bytes(iac, 253, 24))
        val result = negotiator.receive(bytes(iac, 250, 24, 1, iac, 240))
        assertArrayEquals(bytes(iac, 250, 24, 0, 'v'.code, 't'.code, '1'.code, '0'.code, '0'.code, iac, 240), result.replies)
    }

    @Test
    fun commandsSplitAcrossReadsStillWork() {
        val negotiator = TelnetNegotiator()
        val first = negotiator.receive(bytes('a'.code, iac, 251))
        val second = negotiator.receive(bytes(1, 'b'.code))
        assertArrayEquals(bytes('a'.code), first.text)
        assertArrayEquals(bytes(iac, 253, 1), second.replies)
        assertArrayEquals(bytes('b'.code), second.text)
    }

    @Test
    fun outgoingIacIsDoubled() {
        assertArrayEquals(bytes(1, 255, 255, 2), TelnetNegotiator.escape(bytes(1, 255, 2)))
    }

    @Test
    fun oversizedSubnegotiationIsBoundedAndIgnored() {
        val negotiator = TelnetNegotiator()
        val big = ByteArray(5000) { 7 }
        val result = negotiator.receive(bytes(iac, 250, 99) + big + bytes(iac, 240, 'x'.code))
        assertArrayEquals(bytes('x'.code), result.text)
        assertEquals(0, result.replies.size)
    }
}
