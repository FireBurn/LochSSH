package uk.co.fireburn.lochssh.ssh

import com.jcraft.jsch.Proxy
import com.jcraft.jsch.ProxyHTTP
import com.jcraft.jsch.ProxySOCKS5
import com.jcraft.jsch.SocketFactory

enum class SshProxyType { HTTP, SOCKS5 }

data class SshProxySettings(
    val type: SshProxyType,
    val host: String,
    val port: Int,
    val username: String? = null,
    val password: String? = null
) {
    fun validate() {
        require(validHost(host)) { "Enter a proxy address without a scheme or spaces" }
        require(port in 1..65535) { "Proxy port must be from 1 to 65535" }
        require((username == null && password == null) ||
            (!username.isNullOrEmpty() && !password.isNullOrEmpty())) {
            "Enter both the proxy username and password, or leave both blank"
        }
        if (type == SshProxyType.HTTP) {
            require(username?.contains(':') != true) { "HTTP proxy usernames cannot contain a colon" }
        } else {
            require(username == null || username.toByteArray(Charsets.UTF_8).size <= 255) { "Proxy username is too long" }
            require(password == null || password.toByteArray(Charsets.UTF_8).size <= 255) { "Proxy password is too long" }
        }
    }

    internal fun create(): Proxy {
        validate()
        val address = host.removeSurrounding("[", "]")
        val proxy = when (type) {
            SshProxyType.HTTP -> ProxyHTTP(address, port).apply {
                username?.let { setUserPasswd(it, password) }
            }
            SshProxyType.SOCKS5 -> ProxySOCKS5(address, port).apply {
                username?.let { setUserPasswd(it, password) }
            }
        }
        return DestinationProxy(proxy, type)
    }

    override fun toString() = "SshProxySettings(type=$type, host=$host, port=$port, authenticated=${username != null})"

    companion object {
        internal fun validHost(host: String): Boolean = host.isNotEmpty() &&
            host.none { it.isWhitespace() || it.code < 32 || it.code == 127 || it in "/\\?#@" } &&
            ((!host.contains('[') && !host.contains(']')) ||
                (host.startsWith('[') && host.endsWith(']') && host.count { it == '[' || it == ']' } == 2 && host.contains(':')))
    }
}

private class DestinationProxy(private val proxy: Proxy, private val type: SshProxyType) : Proxy by proxy {
    override fun connect(factory: SocketFactory?, host: String, port: Int, timeout: Int) {
        require(SshProxySettings.validHost(host)) { "Invalid SSH server address" }
        val address = host.removeSurrounding("[", "]")
        require(type != SshProxyType.SOCKS5 || address.toByteArray(Charsets.UTF_8).size <= 255) {
            "SSH server address is too long for SOCKS5"
        }
        // HTTP CONNECT needs brackets around an IPv6 destination.
        val target = if (type == SshProxyType.HTTP && address.contains(':')) "[$address]" else address
        proxy.connect(factory, target, port, timeout)
    }
}

internal object SshProxyRoute {
    fun validate(config: SshConnectionConfig) {
        val chain = config.jumpHosts + config
        chain.forEachIndexed { index, host ->
            host.proxy?.let {
                it.validate()
                require(index == 0) { "Set the proxy on the first jump host, not on a host reached through SSH" }
                require(host.knockSequence.isBlank()) { "Port knocking cannot run through a proxy" }
                require(SshProxySettings.validHost(host.host)) { "Invalid SSH server address" }
            }
        }
    }
}
