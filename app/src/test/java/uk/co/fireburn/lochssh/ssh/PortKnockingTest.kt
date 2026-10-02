package uk.co.fireburn.lochssh.ssh

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortKnockingTest {
    @Test
    fun sendsTcpAndUdpToLocalListeners() = runBlocking {
        val address = InetAddress.getByName("127.0.0.1")
        ServerSocket(0, 1, address).use { tcp ->
            DatagramSocket(0, address).use { udp ->
                tcp.soTimeout = 3000
                udp.soTimeout = 3000
                PortKnocking.send("127.0.0.1", listOf(
                    KnockSequence.Step(tcp.localPort, KnockSequence.Protocol.TCP),
                    KnockSequence.Step(udp.localPort, KnockSequence.Protocol.UDP)), 0)
                tcp.accept().use { assertEquals(-1, it.getInputStream().read()) }
                val packet = DatagramPacket(ByteArray(8), 8)
                udp.receive(packet)
                assertEquals(1, packet.length)
                assertEquals(address, packet.address)
            }
        }
    }

    @Test
    fun keepsRepeatedStepsInOrder() = runBlocking {
        val steps = KnockSequence.parse("7000,8000:udp,7000")
        val sent = mutableListOf<KnockSequence.Step>()
        PortKnocking.run(steps, 0) { sent += it }
        assertEquals(steps, sent)
    }

    @Test
    fun cancellationDuringThePauseStopsFurtherPackets() = runBlocking {
        val first = CompletableDeferred<Unit>()
        var sent = 0
        val job = launch {
            PortKnocking.run(KnockSequence.parse("7000,8000"), 5000) {
                sent++
                first.complete(Unit)
            }
        }
        first.await()
        job.cancelAndJoin()
        assertEquals(1, sent)
        assertTrue(job.isCancelled)
    }
}
