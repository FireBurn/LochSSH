package uk.co.fireburn.lochssh.ssh

import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ForwardedIdentityRepositoryTest {
    @Test
    fun exposesOnlyLoadedKeyAndRejectsChanges() {
        val jsch = JSch()
        val pair = KeyPair.genKeyPair(jsch, KeyPair.RSA, 1024)
        try {
            val output = ByteArrayOutputStream()
            pair.writePrivateKey(output)
            val bytes = output.toByteArray()
            try { jsch.addIdentity("test", bytes, null, null) } finally { bytes.fill(0) }
            val repository = ForwardedIdentityRepository(jsch.identityRepository)
            assertEquals(1, repository.identities.size)
            val blob = repository.identities.single().publicKeyBlob
            assertFalse(repository.remove(blob))
            assertFalse(repository.add(byteArrayOf()))
            repository.removeAll()
            repository.identities.clear()
            assertEquals(1, repository.identities.size)
            assertEquals(1, jsch.identityRepository.identities.size)
        } finally { pair.dispose(); jsch.removeAllIdentity() }
    }
}
