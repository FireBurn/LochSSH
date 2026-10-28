package uk.co.fireburn.lochssh.mosh

import java.nio.ByteBuffer
import java.util.Base64
import org.bouncycastle.crypto.InvalidCipherTextException
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.OCBBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter

// A Mosh packet is an 8 byte nonce (direction in the top bit, then a counter) followed by the
// AES-128-OCB ciphertext and 16 byte tag. The nonce is padded with four zero bytes for OCB.
internal class MoshCrypto(private val key: ByteArray) {
    init { require(key.size == 16) { "A Mosh key is 16 bytes" } }

    fun encrypt(nonce: Long, plain: ByteArray): ByteArray {
        val cipher = OCBBlockCipher(AESEngine.newInstance(), AESEngine.newInstance())
        cipher.init(true, AEADParameters(KeyParameter(key), TAG_BITS, nonceBytes(nonce)))
        val out = ByteArray(cipher.getOutputSize(plain.size))
        var length = cipher.processBytes(plain, 0, plain.size, out, 0)
        length += cipher.doFinal(out, length)
        return ByteBuffer.allocate(8 + length).putLong(nonce).put(out, 0, length).array()
    }

    // Null when the packet is not authentic, which is how anything not from the server is dropped.
    fun decrypt(packet: ByteArray): Pair<Long, ByteArray>? {
        if (packet.size < 8 + TAG_BITS / 8) return null
        val nonce = ByteBuffer.wrap(packet, 0, 8).long
        val cipher = OCBBlockCipher(AESEngine.newInstance(), AESEngine.newInstance())
        cipher.init(false, AEADParameters(KeyParameter(key), TAG_BITS, nonceBytes(nonce)))
        val input = packet.copyOfRange(8, packet.size)
        val out = ByteArray(cipher.getOutputSize(input.size))
        return try {
            var length = cipher.processBytes(input, 0, input.size, out, 0)
            length += cipher.doFinal(out, length)
            nonce to out.copyOf(length)
        } catch (_: InvalidCipherTextException) {
            null
        }
    }

    private fun nonceBytes(nonce: Long): ByteArray = ByteBuffer.allocate(12).putInt(0).putLong(nonce).array()

    companion object {
        private const val TAG_BITS = 128
        const val TO_CLIENT = Long.MIN_VALUE

        // The server prints the key as 22 base64 characters without padding.
        fun keyFrom(text: String): ByteArray = Base64.getDecoder().decode(text + "==")
    }
}

internal object MoshServerOutput {
    class Started(val port: Int, val key: ByteArray)

    private val line = Regex("""MOSH CONNECT (\d{1,5}) ([A-Za-z0-9+/]{22})""")

    fun parse(output: String): Started? {
        val match = line.find(output) ?: return null
        val port = match.groupValues[1].toInt()
        if (port !in 1..65535) return null
        return Started(port, MoshCrypto.keyFrom(match.groupValues[2]))
    }
}
