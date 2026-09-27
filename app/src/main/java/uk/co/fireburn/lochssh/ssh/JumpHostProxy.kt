package uk.co.fireburn.lochssh.ssh

import com.jcraft.jsch.ChannelDirectTCPIP
import com.jcraft.jsch.Proxy
import com.jcraft.jsch.Session
import com.jcraft.jsch.SocketFactory
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

internal class JumpHostProxy(private val jump: Session) : Proxy {
    private var channel: ChannelDirectTCPIP? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    override fun connect(socketFactory: SocketFactory?, host: String, port: Int, timeout: Int) {
        check(channel == null) { "Jump connection is already open" }
        val direct = jump.openChannel("direct-tcpip") as ChannelDirectTCPIP
        channel = direct
        try {
            direct.setHost(host)
            direct.setPort(port)
            direct.setOrgIPAddress("127.0.0.1")
            direct.setOrgPort(0)
            input = direct.inputStream
            output = direct.outputStream
            direct.connect(timeout)
            check(direct.isConnected) { "Jump connection could not be opened" }
        } catch (error: Exception) {
            close()
            throw error
        }
    }

    override fun getInputStream(): InputStream = checkNotNull(input)
    override fun getOutputStream(): OutputStream = checkNotNull(output)
    override fun getSocket(): Socket? = null

    override fun close() {
        channel?.disconnect()
        channel = null
        input = null
        output = null
    }
}
