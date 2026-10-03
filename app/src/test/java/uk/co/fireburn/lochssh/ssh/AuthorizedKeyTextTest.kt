package uk.co.fireburn.lochssh.ssh

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AuthorizedKeyTextTest {
    private val key = KeyGenerator.generate("phone").publicKeyOpenSsh

    @Test fun addsOneLineToANewFile() {
        assertEquals("$key\n", AuthorizedKeyText.addition(byteArrayOf(), key).toString(Charsets.UTF_8))
    }

    @Test fun keepsExistingKeysAndAddsAMissingNewline() {
        val existing = "# Keep this\nother key".toByteArray()
        val addition = AuthorizedKeyText.addition(existing, key)
        assertEquals("\n$key\n", addition.toString(Charsets.UTF_8))
        assertEquals("# Keep this\nother key", existing.toString(Charsets.UTF_8))
        assertEquals("$key\n", AuthorizedKeyText.addition("other key\n".toByteArray(), key).toString(Charsets.UTF_8))
    }

    @Test fun skipsAnExistingKeyEvenWithAnotherComment() {
        val publicParts = key.split(' ').take(2).joinToString(" ")
        assertArrayEquals(byteArrayOf(), AuthorizedKeyText.addition("$publicParts old phone\n".toByteArray(), key))
    }

    @Test fun doesNotRemoveRestrictionsFromAnExistingKey() {
        val publicParts = key.split(' ').take(2).joinToString(" ")
        assertArrayEquals(byteArrayOf(), AuthorizedKeyText.addition("restrict,command=\"hello world\" $publicParts old\n".toByteArray(), key))
    }

    @Test fun commentedOutKeysAreNotActive() {
        assertEquals("$key\n", AuthorizedKeyText.addition("# $key\n".toByteArray(), key).toString(Charsets.UTF_8))
    }

    @Test fun rejectsMultipleLinesAndInvalidKeyBlobs() {
        for (bad in listOf("$key\nother key", "$key\r", "$key\u0000", "ssh-ed25519 not-base64", "ssh-rsa AAAA")) {
            assertThrows(IllegalArgumentException::class.java) { AuthorizedKeyText.addition(byteArrayOf(), bad) }
        }
    }

    @Test fun rejectsOversizedFilesBeforeChangingAnything() {
        assertThrows(IllegalArgumentException::class.java) {
            AuthorizedKeyText.addition(ByteArray(AuthorizedKeyText.MAX_BYTES + 1), key)
        }
    }
}
