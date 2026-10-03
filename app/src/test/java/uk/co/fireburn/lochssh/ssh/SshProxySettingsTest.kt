package uk.co.fireburn.lochssh.ssh

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class SshProxySettingsTest {
    private fun config(proxy: SshProxySettings? = null) = SshConnectionConfig(
        "server", 22, "user", "PUBLIC_KEY", null, null, null, null, 30, proxy = proxy)
    private fun proxy(type: SshProxyType = SshProxyType.HTTP) = SshProxySettings(type, "localhost", 8080)

    @Test fun acceptsDirectAndFirstJumpProxies() {
        SshProxyRoute.validate(config(proxy()))
        SshProxyRoute.validate(config().copy(jumpHosts = listOf(config(proxy()), config())))
        proxy(SshProxyType.SOCKS5).copy(host = "[::1]").validate()
    }

    @Test fun rejectsProxiesBehindAnSshJump() {
        assertThrows(IllegalArgumentException::class.java) {
            SshProxyRoute.validate(config(proxy()).copy(jumpHosts = listOf(config())))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SshProxyRoute.validate(config().copy(jumpHosts = listOf(config(), config(proxy()))))
        }
    }

    @Test fun rejectsPortKnockingThroughAProxy() {
        assertThrows(IllegalArgumentException::class.java) {
            SshProxyRoute.validate(config(proxy()).copy(knockSequence = "1234"))
        }
    }

    @Test fun rejectsBadEndpointsAndHttpRequestInjection() {
        for (host in listOf("", "host name", "host\r\nheader", "https://host", "user@host", "[host]")) {
            assertThrows(IllegalArgumentException::class.java) { proxy().copy(host = host).validate() }
            assertThrows(IllegalArgumentException::class.java) { SshProxyRoute.validate(config(proxy()).copy(host = host)) }
        }
        for (port in listOf(0, -1, 65536)) {
            assertThrows(IllegalArgumentException::class.java) { proxy().copy(port = port).validate() }
        }
    }

    @Test fun validatesCredentialsAndCountsSocksBytesRatherThanCharacters() {
        assertThrows(IllegalArgumentException::class.java) { proxy().copy(username = "user").validate() }
        assertThrows(IllegalArgumentException::class.java) { proxy().copy(password = "password").validate() }
        assertThrows(IllegalArgumentException::class.java) { proxy().copy(username = "user:name", password = "password").validate() }
        proxy(SshProxyType.SOCKS5).copy(username = "é".repeat(127), password = "password").validate()
        assertThrows(IllegalArgumentException::class.java) {
            proxy(SshProxyType.SOCKS5).copy(username = "é".repeat(128), password = "password").validate()
        }
        assertThrows(IllegalArgumentException::class.java) {
            proxy(SshProxyType.SOCKS5).copy(username = "user", password = "x".repeat(256)).validate()
        }
    }

    @Test fun doesNotPrintCredentials() {
        val description = proxy().copy(username = "private-user", password = "secret-password").toString()
        assertFalse(description.contains("private-user"))
        assertFalse(description.contains("secret-password"))
    }

    @Test fun rejectsOversizedSocksDestinationsBeforeOpeningASocket() {
        assertThrows(IllegalArgumentException::class.java) {
            proxy(SshProxyType.SOCKS5).create().connect(null, "x".repeat(256), 22, 1_000)
        }
    }
}
