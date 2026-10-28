package uk.co.fireburn.lochssh.mosh

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoshProtocolTest {
    @Test
    fun instructionRoundTripsIncludingTheShutdownNumber() {
        val instruction = Instruction(5, 9, 3, 2, byteArrayOf(1, 2, 3))
        val decoded = Instruction.decode(instruction.encode())!!
        assertEquals(5L, decoded.oldNum)
        assertEquals(9L, decoded.newNum)
        assertEquals(3L, decoded.ackNum)
        assertEquals(2L, decoded.throwawayNum)
        assertArrayEquals(byteArrayOf(1, 2, 3), decoded.diff)
        assertEquals(Instruction.SHUTDOWN, Instruction.decode(Instruction(0, -1, 0, 0, ByteArray(0)).encode())!!.newNum)
    }

    @Test
    fun anInstructionFromAnotherProtocolVersionIsRefused() {
        val out = ByteArrayOutputStream()
        Protobuf.uint(out, 1, 1)
        Protobuf.uint(out, 3, 4)
        assertNull(Instruction.decode(out.toByteArray()))
    }

    @Test
    fun hostBytesAreFoundInsideTheHostMessage() {
        val hostBytes = ByteArrayOutputStream().also { Protobuf.bytes(it, 4, "hello\u001B[0m".toByteArray()) }
        val instruction = ByteArrayOutputStream().also { Protobuf.bytes(it, 2, hostBytes.toByteArray()) }
        val resize = ByteArrayOutputStream().also {
            Protobuf.bytes(it, 3, ByteArrayOutputStream().also { r -> Protobuf.uint(r, 5, 80) }.toByteArray())
        }
        val message = ByteArrayOutputStream().also {
            Protobuf.bytes(it, 1, instruction.toByteArray())
            Protobuf.bytes(it, 1, resize.toByteArray())
        }
        val found = MoshMessages.hostBytes(message.toByteArray())
        assertEquals(1, found.size)
        assertEquals("hello\u001B[0m", String(found[0]))
    }

    @Test
    fun userMessageCarriesKeysAndSize() {
        val message = MoshMessages.userMessage(listOf(MoshMessages.resize(100, 40), MoshMessages.keystroke("ls\r".toByteArray())))
        val entries = Protobuf.read(message)
        assertEquals(2, entries.size)
        val resize = Protobuf.read(Protobuf.read(entries[0].data).single { it.number == 3 }.data)
        assertEquals(100L, resize.single { it.number == 5 }.number64)
        assertEquals(40L, resize.single { it.number == 6 }.number64)
        val keys = Protobuf.read(Protobuf.read(entries[1].data).single { it.number == 2 }.data)
        assertEquals("ls\r", String(keys.single { it.number == 4 }.data))
    }

    @Test
    fun packetsRoundTripAndRefuseTamperingOrAnotherKey() {
        val key = ByteArray(16) { it.toByte() }
        val crypto = MoshCrypto(key)
        val packet = crypto.encrypt(MoshCrypto.TO_CLIENT or 7, "payload".toByteArray())
        val (nonce, plain) = crypto.decrypt(packet)!!
        assertEquals(MoshCrypto.TO_CLIENT or 7, nonce)
        assertEquals("payload", String(plain))
        val tampered = packet.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertNull(crypto.decrypt(tampered))
        assertNull(MoshCrypto(ByteArray(16) { 9 }).decrypt(packet))
    }

    @Test
    fun theServerKeyIsReadFromItsConnectLine() {
        val started = MoshServerOutput.parse("\nMOSH CONNECT 60123 wZWMzsDCjZ1WPEuXCDrDxw\n\nmosh-server (mosh 1.4.0)")!!
        assertEquals(60123, started.port)
        assertEquals(16, started.key.size)
        assertNull(MoshServerOutput.parse("mosh-server needs a UTF-8 native locale to run."))
        assertNull(MoshServerOutput.parse("MOSH CONNECT 70000 wZWMzsDCjZ1WPEuXCDrDxw"))
    }

    @Test
    fun largeInstructionsSplitAndComeBackInAnyOrder() {
        val data = ByteArray(5000).also { java.util.Random(1).nextBytes(it) }
        val pieces = MoshFragments.split(4, data)
        assertTrue(pieces.size > 1)
        val assembler = MoshFragments.Assembler()
        var result: ByteArray? = null
        for (piece in pieces.reversed()) result = assembler.add(piece) ?: result
        assertNotNull(result)
        assertArrayEquals(data, result)
    }
}
