package uk.co.fireburn.lochssh.ssh

import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.KeyPair
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyPairGenerator
import java.util.Base64
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class IdentityLoaderTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun loadsPastedRsaEcdsaAndEd25519Keys() {
        val keys = listOf(
            jschKey(KeyPair.RSA, 2048),
            jschKey(KeyPair.ECDSA, 256),
            KeyGenerator.generate().privateKeyPem
        )
        keys.forEach { key ->
            val jsch = JSch()
            IdentityLoader.add(jsch, null, key, null)
            assertCanSign(jsch)
        }
    }

    @Test
    fun loadsPastedPkcs8Key() {
        val der = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
            .generateKeyPair().private.encoded
        val pem = "-----BEGIN PRIVATE KEY-----\n" +
            Base64.getMimeEncoder(64, byteArrayOf('\n'.code.toByte())).encodeToString(der) +
            "\n-----END PRIVATE KEY-----\n"
        val jsch = JSch()

        IdentityLoader.add(jsch, null, pem, null)

        assertCanSign(jsch)
    }

    @Test
    fun loadsEncryptedPastedKeyWithPassphrase() {
        val jsch = JSch()

        IdentityLoader.add(jsch, null, jschKey(KeyPair.RSA, 2048, "test passphrase"), "test passphrase")

        assertCanSign(jsch)
    }

    @Test
    fun loadsEncryptedOpenSshKeyWithPassphrase() {
        val pair = KeyPair.genKeyPair(JSch(), KeyPair.ED25519)
        val output = ByteArrayOutputStream()
        try {
            pair.writeOpenSSHv1PrivateKey(output, "test passphrase".toByteArray(Charsets.UTF_8))
        } finally {
            pair.dispose()
        }
        val jsch = JSch()

        IdentityLoader.add(jsch, null, output.toString(Charsets.UTF_8.name()), "test passphrase")

        assertCanSign(jsch)
    }

    @Test
    fun loadsKeyFromFilePath() {
        val file = File(folder.root, "id_rsa")
        file.writeText(jschKey(KeyPair.RSA, 2048))
        val jsch = JSch()

        IdentityLoader.add(jsch, file.absolutePath, null, null)

        assertCanSign(jsch)
    }

    @Test
    fun loadsEncryptedKeyFromFilePath() {
        val file = File(folder.root, "id_rsa")
        file.writeText(jschKey(KeyPair.RSA, 2048, "test passphrase"))
        val jsch = JSch()

        IdentityLoader.add(jsch, file.absolutePath, null, "test passphrase")

        assertCanSign(jsch)
    }

    @Test
    fun rejectsEncryptedKeyWithoutPassphrase() {
        val jsch = JSch()
        val key = jschKey(KeyPair.RSA, 2048, "test passphrase")

        assertThrows(JSchException::class.java) {
            IdentityLoader.add(jsch, null, key, null)
        }
    }

    @Test
    fun rejectsWrongPassphrase() {
        val jsch = JSch()
        val key = jschKey(KeyPair.RSA, 2048, "test passphrase")

        assertThrows(JSchException::class.java) {
            IdentityLoader.add(jsch, null, key, "wrong passphrase")
        }
    }

    private fun jschKey(type: Int, size: Int, passphrase: String? = null): String {
        val pair = KeyPair.genKeyPair(JSch(), type, size)
        val output = ByteArrayOutputStream()
        try {
            pair.writePrivateKey(output, passphrase?.toByteArray(Charsets.UTF_8))
            return output.toString(Charsets.UTF_8.name())
        } finally {
            pair.dispose()
        }
    }

    private fun assertCanSign(jsch: JSch) {
        val identity = jsch.identityRepository.identities.single()
        assertFalse(identity.isEncrypted)
        assertNotNull(identity.getSignature("test".toByteArray(Charsets.UTF_8)))
    }
}
