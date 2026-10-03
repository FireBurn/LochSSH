package uk.co.fireburn.lochssh.ssh

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import uk.co.fireburn.lochssh.data.db.AuthTypes
import uk.co.fireburn.lochssh.data.db.IdentityEntity
import uk.co.fireburn.lochssh.data.db.SshHostEntity
import uk.co.fireburn.lochssh.ui.hosts.HostProxyFields

class SavedSshProxyTest {
    private val host = SshHostEntity(name = "server", host = "server", proxyType = "SOCKS5",
        proxyHost = "proxy", proxyPort = 1080, proxyIdentityId = 7)
    private val identity = IdentityEntity(7, "proxy", "proxy-user", AuthTypes.PASSWORD, secretRef = "reference")

    @Test fun resolvesSeparateEncryptedCredentials() = runBlocking {
        val result = SavedSshProxy.resolve(host, { assertEquals(7L, it); identity }, {
            assertEquals("reference", it); "proxy-password"
        })
        assertEquals(SshProxySettings(SshProxyType.SOCKS5, "proxy", 1080, "proxy-user", "proxy-password"), result)
    }

    @Test fun disabledAndAnonymousProxiesDoNotReadCredentials() = runBlocking {
        assertNull(SavedSshProxy.resolve(host.copy(proxyType = "NONE"), { error("Unexpected identity") }, { error("Unexpected secret") }))
        assertEquals(SshProxySettings(SshProxyType.SOCKS5, "proxy", 1080), SavedSshProxy.resolve(
            host.copy(proxyIdentityId = null), { error("Unexpected identity") }, { error("Unexpected secret") }))
    }

    @Test fun rejectsMissingOrUnsuitableIdentities() {
        for (saved in listOf(null, identity.copy(authType = AuthTypes.PUBLIC_KEY), identity.copy(username = ""),
            identity.copy(secretRef = null))) {
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { SavedSshProxy.resolve(host, { saved }, { error("Unexpected secret") }) }
            }
        }
    }

    @Test fun rejectsMissingPasswordsAndOversizedSocksPasswords() {
        for (password in listOf(null, "", "é".repeat(128))) {
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { SavedSshProxy.resolve(host, { identity }, { password }) }
            }
        }
    }

    @Test fun propagatesSecretReadFailuresWithoutConnectingAnonymously() {
        assertThrows(java.io.IOException::class.java) {
            runBlocking { SavedSshProxy.resolve(host, { identity }, { throw java.io.IOException("Unavailable") }) }
        }
    }

    @Test fun disablingClearsSavedFieldsAndIgnoresHiddenInput() {
        val saved = HostProxyFields(host = "bad address", port = "invalid", identityId = 7).applyTo(host)
        assertEquals("NONE", saved.proxyType)
        assertEquals("", saved.proxyHost)
        assertEquals(1080, saved.proxyPort)
        assertNull(saved.proxyIdentityId)
    }

    @Test fun validatesTheSavedRouteAndNormalizesAddressWhitespace() {
        val saved = HostProxyFields("HTTP", " proxy ", "8080").applyTo(host)
        assertEquals("proxy", saved.proxyHost)
        assertEquals(HostProxyFields("HTTP", "proxy", "8080"), HostProxyFields.from(saved))
        SavedSshProxy.checkRoute(listOf(saved, host.copy(proxyType = "NONE")))
        assertThrows(IllegalArgumentException::class.java) { SavedSshProxy.checkRoute(listOf(saved, host)) }
        assertThrows(IllegalArgumentException::class.java) { SavedSshProxy.checkRoute(listOf(saved.copy(knockSequence = "1234"))) }
    }
}
