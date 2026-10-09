package uk.co.fireburn.lochssh.data

import java.security.GeneralSecurityException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCipherTest {
    private val passphrase = "Public test passphrase 编译".toCharArray()
    private val cipher = BackupCipher()

    @Test fun roundTripPreservesRawBytesAndUsesFreshSaltAndNonce() {
        val contents = byteArrayOf(0, 1, -1, 27, -16, -97, -104, -128)
        val first = cipher.encrypt(contents, passphrase)
        val second = cipher.encrypt(contents, passphrase)
        assertFalse(first.contentEquals(second))
        assertArrayEquals(contents, cipher.decrypt(first, passphrase))
        assertArrayEquals(contents, cipher.decrypt(second, passphrase))
        assertArrayEquals("Public test passphrase 编译".toCharArray(), passphrase)
    }

    @Test fun wrongPassphraseOrChangedSaltNonceAndPayloadCannotReturnPlaintext() {
        val backup = cipher.encrypt("Public fixture data".toByteArray(), passphrase)
        assertThrows(GeneralSecurityException::class.java) {
            cipher.decrypt(backup, "Different test passphrase".toCharArray())
        }
        for (offset in listOf(13, 29, backup.lastIndex)) {
            val changed = backup.copyOf()
            changed[offset] = (changed[offset].toInt() xor 1).toByte()
            assertThrows(GeneralSecurityException::class.java) { cipher.decrypt(changed, passphrase) }
        }
    }

    @Test fun rejectsUnknownFormatWorkFactorsTruncationAndTrailingData() {
        val backup = cipher.encrypt(byteArrayOf(1), passphrase)
        for (offset in listOf(0, 8, 9, 41)) {
            val changed = backup.copyOf()
            changed[offset] = (changed[offset].toInt() xor 1).toByte()
            assertThrows(IllegalArgumentException::class.java) { cipher.decrypt(changed, passphrase) }
        }
        assertThrows(IllegalArgumentException::class.java) { cipher.decrypt(backup.dropLast(1).toByteArray(), passphrase) }
        assertThrows(IllegalArgumentException::class.java) { cipher.decrypt(backup + byteArrayOf(1), passphrase) }
        assertThrows(IllegalArgumentException::class.java) { cipher.decrypt(byteArrayOf(), passphrase) }
    }

    @Test fun rejectsOversizedFilesAndShortPassphrases() {
        assertThrows(IllegalArgumentException::class.java) { cipher.encrypt(byteArrayOf(), "short".toCharArray()) }
        assertThrows(IllegalArgumentException::class.java) {
            cipher.decrypt(ByteArray(BackupCipher.MAX_FILE_SIZE + 1), passphrase)
        }
    }
}
