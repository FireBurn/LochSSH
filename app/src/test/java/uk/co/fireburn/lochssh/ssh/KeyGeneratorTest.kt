package uk.co.fireburn.lochssh.ssh

import com.jcraft.jsch.JSch
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class KeyGeneratorTest {
    @Test
    fun generatedKeyLoadsInJschAndContainsTheFullEd25519PrivateKey() {
        val generated = KeyGenerator.generate("test key")
        val publicBlob = Base64.getDecoder().decode(generated.publicKeyOpenSsh.split(' ')[1])

        val jsch = JSch()
        jsch.addIdentity("generated", generated.privateKeyPem.toByteArray(UTF_8), null, null)
        val identity = jsch.identityRepository.identities.single()
        assertEquals("ssh-ed25519", identity.algName)
        assertFalse(identity.isEncrypted)
        assertArrayEquals(publicBlob, identity.publicKeyBlob)
        assertNotNull(identity.getSignature("message".toByteArray(UTF_8)))

        val encoded = generated.privateKeyPem.lineSequence()
            .filterNot { it.startsWith("-----") }
            .joinToString("")
        val key = ByteBuffer.wrap(Base64.getDecoder().decode(encoded))
        val magic = ByteArray("openssh-key-v1\u0000".toByteArray(UTF_8).size)
        key.get(magic)
        assertArrayEquals("openssh-key-v1\u0000".toByteArray(UTF_8), magic)
        assertEquals("none", key.readString().toString(UTF_8))
        assertEquals("none", key.readString().toString(UTF_8))
        assertEquals(0, key.readString().size)
        assertEquals(1, key.int)
        assertArrayEquals(publicBlob, key.readString())

        val privateBlock = ByteBuffer.wrap(key.readString())
        assertEquals(privateBlock.int, privateBlock.int)
        assertEquals("ssh-ed25519", privateBlock.readString().toString(UTF_8))
        val rawPublic = privateBlock.readString()
        val rawPrivate = privateBlock.readString()
        assertEquals(32, rawPublic.size)
        assertEquals(64, rawPrivate.size)
        assertArrayEquals(rawPublic, rawPrivate.copyOfRange(32, 64))
        assertEquals("test key", privateBlock.readString().toString(UTF_8))
    }

    private fun ByteBuffer.readString(): ByteArray {
        val size = int
        require(size >= 0 && size <= remaining())
        return ByteArray(size).also { get(it) }
    }
}
