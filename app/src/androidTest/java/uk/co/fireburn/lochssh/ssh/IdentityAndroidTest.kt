package uk.co.fireburn.lochssh.ssh

import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.KeyPair
import com.jcraft.jsch.android.AndroidJSch
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class IdentityAndroidTest {
    @Before
    fun configureAndroidSigning() {
        AndroidJSch.configure()
    }

    @Test
    fun generatedKeyLoadsAndSigns() {
        val generated = KeyGenerator.generate()
        val jsch = JSch()

        IdentityLoader.add(jsch, null, generated.privateKeyPem, null)

        val identity = jsch.identityRepository.identities.single()
        assertEquals(generated.publicKeyOpenSsh.substringBefore(' '), identity.algName)
        assertFalse(identity.isEncrypted)
        assertNotNull(identity.getSignature("test".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun encryptedRsaKeyLoadsAndSigns() {
        val jsch = JSch()

        IdentityLoader.add(jsch, null, encryptedRsaKey(), "test passphrase")

        val identity = jsch.identityRepository.identities.single()
        assertFalse(identity.isEncrypted)
        assertNotNull(identity.getSignature("test".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun encryptedRsaKeyNeedsPassphrase() {
        assertThrows(JSchException::class.java) {
            IdentityLoader.add(JSch(), null, encryptedRsaKey(), null)
        }
    }

    private fun encryptedRsaKey(): String {
        val pair = KeyPair.genKeyPair(JSch(), KeyPair.RSA, 2048)
        val output = ByteArrayOutputStream()
        try {
            pair.writePrivateKey(output, "test passphrase".toByteArray(Charsets.UTF_8))
            return output.toString(Charsets.UTF_8.name())
        } finally {
            pair.dispose()
        }
    }
}
