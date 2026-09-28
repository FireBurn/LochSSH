package uk.co.fireburn.lochssh.ssh

import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import java.io.File
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HostKeyApprovalTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun stopsUnknownHandshakeUntilExactKeyIsApproved() {
        val key = System.getenv("LOCHSSH_TEST_SSH_KEY")
        val user = System.getenv("LOCHSSH_TEST_SSH_USER")
        val host = System.getenv("LOCHSSH_TEST_SSH_HOST")
        assumeTrue(!key.isNullOrBlank() && !user.isNullOrBlank() && !host.isNullOrBlank())
        val store = PersistentHostKeys(File(folder.root, "known_hosts"))
        val jsch = JSch().apply {
            addIdentity(requireNotNull(key))
            setHostKeyRepository(store)
        }
        val first = jsch.getSession(requireNotNull(user), requireNotNull(host), 22)
        first.setConfig("StrictHostKeyChecking", "yes")
        val review = try {
            first.connect(5000)
            throw AssertionError("Unknown server key was accepted")
        } catch (required: HostKeyReviewRequired) { required.key }
        finally { first.disconnect() }
        assertTrue(store.saved().isEmpty())

        val reference = JSch().apply {
            setKnownHosts(File(System.getProperty("user.home"), ".ssh/known_hosts").absolutePath)
        }
        assertEquals(HostKeyRepository.OK,
            reference.hostKeyRepository.check(review.host, Base64.getDecoder().decode(review.key)))
        store.accept(review)
        val retry = jsch.getSession(user, host, 22)
        retry.setConfig("StrictHostKeyChecking", "yes")
        try {
            retry.connect(5000)
            assertTrue(retry.isConnected)
            assertEquals(review, store.saved().single())
        } finally {
            retry.disconnect()
            jsch.removeAllIdentity()
        }
    }
}
