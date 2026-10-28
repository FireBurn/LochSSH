package uk.co.fireburn.lochssh.serial

import java.io.IOException
import java.util.concurrent.LinkedBlockingQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SerialTest {
    @Test
    fun settingsRoundTripAndDefaultToTheCommonLine() {
        val settings = SerialSettings(9600, 7, SerialSettings.Parity.EVEN, SerialSettings.StopBits.TWO,
            SerialSettings.Flow.RTS_CTS, "0403:6001")
        assertEquals(settings, SerialSettings.decode(settings.encode()))
        assertEquals(SerialSettings(), SerialSettings.decode(""))
        assertEquals(SerialSettings(), SerialSettings.decode("garbage,1,2"))
        assertEquals(115_200, SerialSettings().baud)
        assertEquals(8, SerialSettings().dataBits)
    }

    @Test
    fun settingsRefuseValuesAnAdapterCannotUse() {
        for (bad in listOf({ SerialSettings(baud = 10) }, { SerialSettings(baud = 5_000_000) },
            { SerialSettings(dataBits = 4) }, { SerialSettings(dataBits = 9) }, { SerialSettings(device = "usb0") })) {
            try { bad(); fail("expected a refusal") } catch (_: IllegalArgumentException) { }
        }
    }

    @Test
    fun theDeviceIdIsReadAsHexVendorAndProduct() {
        assertEquals(0x0403 to 0x6001, SerialSettings(device = "0403:6001").deviceNumbers())
        assertNull(SerialSettings().deviceNumbers())
    }

    private class FakePort : SerialIo {
        val fromDevice = LinkedBlockingQueue<ByteArray>()
        val toDevice = ArrayList<ByteArray>()
        @Volatile var unplugged = false
        @Volatile var closed = false

        override fun read(buffer: ByteArray, timeoutMs: Int): Int {
            if (unplugged) throw IOException("gone")
            val data = fromDevice.poll(timeoutMs.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS) ?: return 0
            System.arraycopy(data, 0, buffer, 0, data.size)
            return data.size
        }

        override fun write(data: ByteArray) { toDevice.add(data) }
        override fun close() { closed = true }
    }

    @Test
    fun bytesFlowBothWaysThroughTheConnection() {
        val port = FakePort()
        val connection = SerialConnection(port)
        connection.output.write("AT\r".toByteArray())
        assertEquals("AT\r", String(port.toDevice.single()))
        port.fromDevice.put("OK\r\n".toByteArray())
        val buffer = ByteArray(16)
        val count = connection.input.read(buffer)
        assertEquals("OK\r\n", String(buffer, 0, count))
        connection.close()
        assertTrue(port.closed)
        assertFalse(connection.isOpen)
        assertEquals(-1, connection.input.read(buffer))
    }

    @Test
    fun pullingTheAdapterEndsTheSessionWithAClearMessage() {
        val port = FakePort()
        val connection = SerialConnection(port)
        port.unplugged = true
        try {
            connection.input.read(ByteArray(8))
            fail("expected the read to fail")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("disconnected"))
        }
        assertFalse(connection.isOpen)
        assertTrue(port.closed)
        try { connection.output.write(1); fail("expected a refusal") } catch (_: IOException) { }
    }
}
