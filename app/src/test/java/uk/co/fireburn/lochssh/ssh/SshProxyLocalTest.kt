package uk.co.fireburn.lochssh.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Test

class SshProxyLocalTest {
    @Test fun authenticatesSshThroughHttpConnect() = ssh(SshProxyType.HTTP, false)
    @Test fun authenticatesSshThroughPasswordProtectedHttpConnect() = ssh(SshProxyType.HTTP, true)
    @Test fun authenticatesSshThroughSocks5() = ssh(SshProxyType.SOCKS5, false)
    @Test fun authenticatesSshThroughPasswordProtectedSocks5() = ssh(SshProxyType.SOCKS5, true)

    @Test fun doesNotFallBackWhenProxyAuthenticationFails() {
        for (type in SshProxyType.entries) {
            LoopbackSshProxy(type, "localhost", authenticated = true, relay = false).use { server ->
                val settings = SshProxySettings(type, "127.0.0.1", server.port, "proxy-user", "wrong-password")
                assertThrows(JSchException::class.java) { settings.create().connect(null, "localhost", 22, 2_000) }
                assertEquals("rejected", server.target())
            }
        }
    }

    @Test fun bracketsIpv6HttpDestinations() {
        LoopbackSshProxy(SshProxyType.HTTP, "::1", relay = false).use { server ->
            val proxy = SshProxySettings(SshProxyType.HTTP, "127.0.0.1", server.port).create()
            try {
                proxy.connect(null, "::1", 22, 2_000)
                assertEquals("[::1]:22", server.target())
            } finally { proxy.close() }
        }
    }

    private fun ssh(type: SshProxyType, authenticated: Boolean) {
        val key = System.getenv("LOCHSSH_TEST_SSH_KEY")
        val user = System.getenv("LOCHSSH_TEST_SSH_USER")
        val host = System.getenv("LOCHSSH_TEST_SSH_HOST")
        assumeTrue(!key.isNullOrBlank() && !user.isNullOrBlank() && !host.isNullOrBlank())
        LoopbackSshProxy(type, requireNotNull(host), authenticated).use { server ->
            val settings = SshProxySettings(type, "127.0.0.1", server.port,
                if (authenticated) "proxy-user" else null, if (authenticated) "proxy-password" else null)
            val ssh = JSch().apply {
                setKnownHosts(File(System.getProperty("user.home"), ".ssh/known_hosts").absolutePath)
                addIdentity(requireNotNull(key))
            }
            val session = ssh.getSession(requireNotNull(user), host, 22)
            session.setProxy(settings.create())
            session.setConfig("StrictHostKeyChecking", "yes")
            session.setConfig("PreferredAuthentications", "publickey")
            session.setTimeout(5_000)
            try {
                session.connect(5_000)
                assertEquals("$host:22", server.target())
                val channel = session.openChannel("exec") as ChannelExec
                try {
                    channel.setCommand("printf proxy-ok")
                    val input = channel.inputStream
                    channel.connect(5_000)
                    assertEquals("proxy-ok", input.readBytes().toString(Charsets.UTF_8))
                } finally { channel.disconnect() }
            } finally { session.disconnect() }
        }
    }
}
