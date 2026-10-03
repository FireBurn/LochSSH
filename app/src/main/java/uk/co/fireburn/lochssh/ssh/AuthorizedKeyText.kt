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
        if (existing.toString(Charsets.UTF_8).lineSequence().any {
                containsKey(it, fields[0], fields[1])
            }) return byteArrayOf()
        val separator = if (existing.isNotEmpty() && existing.last() != '\n'.code.toByte()) "\n" else ""
        return "$separator${publicKey.trim()}\n".toByteArray(Charsets.UTF_8)
    }

    private fun containsKey(line: String, type: String, blob: String): Boolean {
        if (line.trimStart().startsWith("#")) return false
        val fields = columns(line, type)
        val offset = if (fields.firstOrNull() == type) 0 else 1
        return fields.getOrNull(offset) == type && fields.getOrNull(offset + 1) == blob
    }

    private fun columns(line: String, type: String): List<String> {
        val result = mutableListOf<String>()
        var index = 0
        while (index < line.length && result.size < 3) {
            while (index < line.length && line[index].isWhitespace()) index++
            if (index == line.length) break
            val start = index
            var quoted = false
            var escaped = false
            while (index < line.length) {
                val character = line[index]
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> quoted = !quoted
                    character.isWhitespace() && !quoted -> break
                }
                index++
            }
            if (quoted || escaped) return emptyList()
            result += line.substring(start, index)
            if (result.size == 2 && result.first() == type) break
        }
        return result
    }
}
