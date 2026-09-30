package uk.co.fireburn.lochssh.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.io.File
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Socket
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class Ipv6ConnectionTest {
    @Test
    fun authenticatesAndRunsACommandOverIpv6() = withSession { session, _ ->
        val channel = session.openChannel("exec") as ChannelExec
        try {
            channel.setCommand("printf ipv6")
            val input = channel.inputStream
            channel.connect(3_000)
            assertEquals("ipv6", input.readNBytes(4).toString(Charsets.UTF_8))
        } finally { channel.disconnect() }
    }

    @Test
    fun localForwardReachesAnIpv6Destination() = withSession { session, host ->
        val port = session.setPortForwardingL(0, host, 22)
        try {
            Socket("127.0.0.1", port).use {
                it.soTimeout = 3_000
                assertEquals("SSH-", it.getInputStream().readNBytes(4).toString(Charsets.US_ASCII))
            }
        } finally { session.delPortForwardingL(port) }
    }

    @Test
    fun socksProxyAcceptsAnIpv6Address() = withSession { session, host ->
        val proxy = Socks5Forwarder(session, 0)
        proxy.start()
        try {
            Socket("127.0.0.1", proxy.listenPort).use {
                it.soTimeout = 3_000
                val output = it.getOutputStream()
                val input = it.getInputStream()
                output.write(byteArrayOf(5, 1, 0))
                output.flush()
                assertArrayEquals(byteArrayOf(5, 0), input.readNBytes(2))
                output.write(byteArrayOf(5, 1, 0, 4) + InetAddress.getByName(host).address + byteArrayOf(0, 22))
                output.flush()
                val reply = input.readNBytes(10)
                assertEquals(10, reply.size)
                assertEquals(0, reply[1].toInt())
                assertEquals("SSH-", input.readNBytes(4).toString(Charsets.US_ASCII))
            }
        } finally { proxy.stop() }
    }

    private fun withSession(check: (Session, String) -> Unit) {
        val host = System.getenv("LOCHSSH_TEST_SSH_IPV6_HOST")
        val trustedHost = System.getenv("LOCHSSH_TEST_SSH_HOST")
        val user = System.getenv("LOCHSSH_TEST_SSH_USER")
        val key = System.getenv("LOCHSSH_TEST_SSH_KEY")
        assumeTrue(listOf(host, trustedHost, user, key).all { !it.isNullOrBlank() })
        val address = requireNotNull(host)
        assertTrue(InetAddress.getByName(address) is Inet6Address)
        val known = JSch().apply {
            File(System.getProperty("user.home"), ".ssh/known_hosts").inputStream().use { setKnownHosts(it) }
        }
        val keys = known.hostKeyRepository.getHostKey(requireNotNull(trustedHost), null).orEmpty()
        assertTrue("The server must already have a trusted key", keys.isNotEmpty())
        val aliases = keys.joinToString("\n", postfix = "\n") {
            listOf(it.marker.orEmpty(), address, it.type, it.key).filter(String::isNotBlank).joinToString(" ")
        }
        val jsch = JSch().apply {
            aliases.byteInputStream(Charsets.US_ASCII).use { setKnownHosts(it) }
            addIdentity(requireNotNull(key))
        }
        val session = jsch.getSession(requireNotNull(user), address, 22)
        session.setConfig("StrictHostKeyChecking", "yes")
        session.setConfig("PreferredAuthentications", "publickey")
        session.connect(5_000)
        try { check(session, address) }
        finally { session.disconnect() }
    }
}
