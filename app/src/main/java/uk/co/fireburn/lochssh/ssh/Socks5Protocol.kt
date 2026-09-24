package uk.co.fireburn.lochssh.ssh

import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.nio.charset.StandardCharsets

internal data class Socks5Request(val host: String, val port: Int)

internal object Socks5Protocol {
    fun readRequest(input: InputStream, output: OutputStream): Socks5Request? {
        val data = DataInputStream(input)
        if (data.readUnsignedByte() != 5) return null
        val methodCount = data.readUnsignedByte()
        if (methodCount == 0) {
            output.write(byteArrayOf(5, 0xFF.toByte()))
            output.flush()
            return null
        }
        val methods = ByteArray(methodCount)
        data.readFully(methods)
        if (methods.none { it.toInt() == 0 }) {
            output.write(byteArrayOf(5, 0xFF.toByte()))
            output.flush()
            return null
        }
        output.write(byteArrayOf(5, 0))
        output.flush()

        val version = data.readUnsignedByte()
        val command = data.readUnsignedByte()
        val reserved = data.readUnsignedByte()
        val addressType = data.readUnsignedByte()
        if (version != 5 || reserved != 0) {
            reply(output, 1)
            return null
        }
        if (command != 1) {
            reply(output, 7)
            return null
        }
        val host = when (addressType) {
            1 -> InetAddress.getByAddress(ByteArray(4).also { data.readFully(it) }).hostAddress
            3 -> {
                val length = data.readUnsignedByte()
                if (length == 0) {
                    reply(output, 8)
                    return null
                }
                String(ByteArray(length).also { data.readFully(it) }, StandardCharsets.US_ASCII)
            }
            4 -> InetAddress.getByAddress(ByteArray(16).also { data.readFully(it) }).hostAddress
            else -> {
                reply(output, 8)
                return null
            }
        }
        val port = data.readUnsignedShort()
        if (port == 0) {
            reply(output, 1)
            return null
        }
        return Socks5Request(host, port)
    }

    fun reply(output: OutputStream, status: Int) {
        output.write(byteArrayOf(5, status.toByte(), 0, 1, 0, 0, 0, 0, 0, 0))
        output.flush()
    }
}
