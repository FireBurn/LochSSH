package uk.co.fireburn.lochssh.ssh

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Socks5ProtocolTest {
    @Test
    fun readsDomainWithoutResolvingItLocally() {
        val host = "example.org".toByteArray(Charsets.US_ASCII)
        val input = byteArrayOf(5, 1, 0, 5, 1, 0, 3, host.size.toByte()) +
            host + byteArrayOf(0, 80)
        val output = ByteArrayOutputStream()

        assertEquals(
            Socks5Request("example.org", 80),
            Socks5Protocol.readRequest(ByteArrayInputStream(input), output)
        )
        assertArrayEquals(byteArrayOf(5, 0), output.toByteArray())
    }

    @Test
    fun readsIpv4Address() {
        val input = byteArrayOf(5, 1, 0, 5, 1, 0, 1, 127, 0, 0, 1, 0, 22)

        assertEquals(
            Socks5Request("127.0.0.1", 22),
            Socks5Protocol.readRequest(ByteArrayInputStream(input), ByteArrayOutputStream())
        )
    }

    @Test
    fun rejectsMissingNoAuthMethod() {
        val output = ByteArrayOutputStream()

        assertNull(Socks5Protocol.readRequest(ByteArrayInputStream(byteArrayOf(5, 1, 2)), output))
        assertArrayEquals(byteArrayOf(5, 0xFF.toByte()), output.toByteArray())
    }

    @Test
    fun rejectsEmptyMethodList() {
        val output = ByteArrayOutputStream()

        assertNull(Socks5Protocol.readRequest(ByteArrayInputStream(byteArrayOf(5, 0)), output))
        assertArrayEquals(byteArrayOf(5, 0xFF.toByte()), output.toByteArray())
    }

    @Test
    fun rejectsUnsupportedCommand() {
        val output = ByteArrayOutputStream()
        val input = byteArrayOf(5, 1, 0, 5, 2, 0, 1)

        assertNull(Socks5Protocol.readRequest(ByteArrayInputStream(input), output))
        assertEquals(7, output.toByteArray()[3].toInt())
    }
}
