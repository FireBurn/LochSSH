package uk.co.fireburn.lochssh.ssh

import com.jcraft.jsch.HostKeyRepository
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PersistentHostKeysTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun acceptsNewKeyAndRecognizesItAfterReload() {
        val file = File(folder.root, "known_hosts")
        val key = ed25519Key(1)

        assertEquals(HostKeyRepository.OK, PersistentHostKeys(file).check("example.org", key))
        assertTrue(file.length() > 0)
        assertEquals(HostKeyRepository.OK, PersistentHostKeys(file).check("example.org", key))
    }

    @Test
    fun rejectsChangedKeyWithoutReplacingSavedKey() {
        val file = File(folder.root, "known_hosts")
        val first = ed25519Key(1)
        val second = ed25519Key(2)
        val store = PersistentHostKeys(file)

        assertEquals(HostKeyRepository.OK, store.check("example.org", first))
        assertEquals(HostKeyRepository.CHANGED, store.check("example.org", second))
        assertEquals(HostKeyRepository.OK, PersistentHostKeys(file).check("example.org", first))
    }

    @Test
    fun rejectsAnotherKeyTypeForKnownHost() {
        val store = PersistentHostKeys(File(folder.root, "known_hosts"))
        val rsa = byteArrayOf(0, 0, 0, 7) + "ssh-rsa".toByteArray(Charsets.US_ASCII) +
            byteArrayOf(0, 0, 0, 1, 1)

        assertEquals(HostKeyRepository.OK, store.check("example.org", ed25519Key(1)))
        assertEquals(HostKeyRepository.CHANGED, store.check("example.org", rsa))
    }

    @Test
    fun listsAndRemovesOnlySelectedKey() {
        val file = File(folder.root, "known_hosts")
        val store = PersistentHostKeys(file)
        val first = ed25519Key(1)
        val second = ed25519Key(2)
        store.check("first.example", first)
        store.check("second.example", second)

        val keys = store.saved()
        assertEquals(2, keys.size)
        assertEquals("ssh-ed25519", keys.first().type)
        assertTrue(keys.first().fingerprint.isNotBlank())
        store.removeSaved(keys.first())

        val reopened = PersistentHostKeys(file)
        assertTrue(reopened.saved().none { it.host == "first.example" })
        assertEquals(HostKeyRepository.OK, reopened.check("second.example", second))
    }

    @Test
    fun clearRemovesSavedKeys() {
        val file = File(folder.root, "known_hosts")
        val store = PersistentHostKeys(file)
        store.check("example.org", ed25519Key(1))
        store.clear()

        assertTrue(store.saved().isEmpty())
        assertTrue(PersistentHostKeys(file).saved().isEmpty())
    }

    private fun ed25519Key(value: Byte): ByteArray {
        val name = "ssh-ed25519".toByteArray(Charsets.US_ASCII)
        return byteArrayOf(0, 0, 0, name.size.toByte()) + name +
            byteArrayOf(0, 0, 0, 32) + ByteArray(32) { value }
    }
}
