package uk.co.fireburn.lochssh.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class KnockSequenceTest {
    @Test
    fun keepsOrderRepeatedPortsAndProtocols() {
        assertEquals(listOf(KnockSequence.Step(7000, KnockSequence.Protocol.TCP),
            KnockSequence.Step(8000, KnockSequence.Protocol.UDP),
            KnockSequence.Step(7000, KnockSequence.Protocol.TCP)),
            KnockSequence.parse("7000, 8000:UDP, 7000:tcp"))
    }

    @Test
    fun emptySequenceIsDisabledAndBoundaryPortsAreAccepted() {
        assertEquals(emptyList<KnockSequence.Step>(), KnockSequence.parse(" \n"))
        assertEquals(listOf(1, 65535), KnockSequence.parse("1,65535").map { it.port })
    }

    @Test
    fun rejectsInvalidPortsProtocolsEmptyStepsAndExcessiveLength() {
        for (text in listOf("0", "65536", "-1", "+22", "22.0", "22:http", "22:",
            "22:tcp:udp", "22,", ",22", "22,,23", "99999999999999999999",
            List(33) { "22" }.joinToString(","), "2".repeat(513))) {
            assertThrows(text, IllegalArgumentException::class.java) { KnockSequence.parse(text) }
        }
    }
}
