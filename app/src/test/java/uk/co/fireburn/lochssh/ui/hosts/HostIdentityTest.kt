package uk.co.fireburn.lochssh.ui.hosts

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import uk.co.fireburn.lochssh.data.db.AuthTypes
import uk.co.fireburn.lochssh.data.db.IdentityEntity

class HostIdentityTest {
    @Test
    fun permitsHostsWithoutAnIdentity() = runBlocking {
        HostIdentity.check(null, false) { error("Unexpected lookup") }
    }

    @Test
    fun rejectsAMissingSelectedIdentity() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { HostIdentity.check(7, false) { null } }
        }
        assertEquals("The selected identity no longer exists", error.message)
    }

    @Test
    fun permitsEveryExistingAuthenticationTypeWithoutForwarding() = runBlocking {
        listOf(AuthTypes.NONE, AuthTypes.PASSWORD, AuthTypes.PUBLIC_KEY).forEach { type ->
            HostIdentity.check(7, false) {
                assertEquals(7L, it)
                IdentityEntity(7, "Test", "user", type)
            }
        }
    }

    @Test
    fun forwardingRequiresAPrivateKeyIdentity() {
        listOf(AuthTypes.NONE, AuthTypes.PASSWORD).forEach { type ->
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { HostIdentity.check(7, true) { IdentityEntity(7, "Test", "user", type) } }
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { HostIdentity.check(null, true) { error("Unexpected lookup") } }
        }
        runBlocking {
            HostIdentity.check(7, true) { IdentityEntity(7, "Test", "user", AuthTypes.PUBLIC_KEY) }
        }
    }
}
