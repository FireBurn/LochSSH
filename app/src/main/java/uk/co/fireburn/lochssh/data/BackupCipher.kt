package uk.co.fireburn.lochssh.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal class BackupCipher(private val random: SecureRandom = SecureRandom()) {
    fun encrypt(contents: ByteArray, passphrase: CharArray): ByteArray {
        require(contents.size <= MAX_CONTENTS) { "Backup is too large" }
        val salt = ByteArray(16).also(random::nextBytes)
        val nonce = ByteArray(12).also(random::nextBytes)
        val header = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use {
                it.write(MAGIC)
                it.writeByte(1)
                it.writeInt(ITERATIONS)
                it.write(salt)
                it.write(nonce)
                it.writeInt(contents.size + 16)
            }
        }.toByteArray()
        return header + transform(Cipher.ENCRYPT_MODE, passphrase, salt, nonce, header, contents)
    }

    fun decrypt(backup: ByteArray, passphrase: CharArray): ByteArray {
        require(backup.size in HEADER_SIZE + 16..MAX_FILE_SIZE) { "Invalid backup size" }
        val header = backup.copyOfRange(0, HEADER_SIZE)
        val salt = ByteArray(16)
        val nonce = ByteArray(12)
        DataInputStream(ByteArrayInputStream(header)).use {
            val magic = ByteArray(MAGIC.size).also(it::readFully)
            require(magic.contentEquals(MAGIC) && it.readUnsignedByte() == 1) { "Unknown backup format" }
            require(it.readInt() == ITERATIONS) { "Unsupported backup encryption" }
            it.readFully(salt)
            it.readFully(nonce)
            require(it.readInt() == backup.size - HEADER_SIZE) { "Invalid backup length" }
        }
        return transform(Cipher.DECRYPT_MODE, passphrase, salt, nonce, header,
            backup.copyOfRange(HEADER_SIZE, backup.size))
    }

    private fun transform(mode: Int, passphrase: CharArray, salt: ByteArray,
        nonce: ByteArray, header: ByteArray, contents: ByteArray): ByteArray {
        require(passphrase.size in 12..1024) { "Use a passphrase of 12 to 1024 characters" }
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, 256)
        val key = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
            finally { spec.clearPassword() }
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(header)
            return cipher.doFinal(contents)
        } finally { key.fill(0) }
    }

    companion object {
        private val MAGIC = "LSSHBACK".toByteArray(Charsets.US_ASCII)
        private const val ITERATIONS = 600_000
        private const val HEADER_SIZE = 45
        private const val MAX_CONTENTS = 16 * 1024 * 1024
        const val MAX_FILE_SIZE = HEADER_SIZE + MAX_CONTENTS + 16
    }
}
