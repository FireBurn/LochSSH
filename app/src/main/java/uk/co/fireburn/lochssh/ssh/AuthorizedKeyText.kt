package uk.co.fireburn.lochssh.ssh

import java.util.Base64

object AuthorizedKeyText {
    const val MAX_BYTES = 1_048_576

    fun addition(existing: ByteArray, publicKey: String): ByteArray {
        require(existing.size <= MAX_BYTES) { "The authorized_keys file is too large" }
        require(publicKey.length <= 16_384 && publicKey.none { it == '\n' || it == '\r' || it == '\u0000' }) {
            "Invalid public key"
        }
        val fields = publicKey.trim().split(Regex("\\s+"), limit = 3)
        require(fields.size >= 2 && fields[0] in setOf("ssh-ed25519", "ssh-rsa")) { "Invalid public key" }
        val blob = Base64.getDecoder().decode(fields[1])
        require(blob.size >= 4) { "Invalid public key" }
        val length = java.nio.ByteBuffer.wrap(blob).int
        require(length in 1..64 && blob.size > 4 + length &&
            blob.copyOfRange(4, 4 + length).toString(Charsets.US_ASCII) == fields[0]) { "Invalid public key" }
        val matching = Regex("(?:^|\\s)${Regex.escape(fields[0])}\\s+${Regex.escape(fields[1])}(?:\\s|$)")
        if (existing.toString(Charsets.UTF_8).lineSequence().any {
                !it.trimStart().startsWith("#") && matching.containsMatchIn(it)
            }) return byteArrayOf()
        val separator = if (existing.isNotEmpty() && existing.last() != '\n'.code.toByte()) "\n" else ""
        return "$separator${publicKey.trim()}\n".toByteArray(Charsets.UTF_8)
    }
}
