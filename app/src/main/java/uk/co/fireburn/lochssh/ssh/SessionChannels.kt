package uk.co.fireburn.lochssh.ssh

import com.jcraft.jsch.Channel
import java.io.IOException

internal class SessionChannels {
    private val channels = mutableSetOf<Channel>()
    private var closed = false

    @Synchronized
    fun add(channel: Channel) {
        if (closed) throw IOException("Terminal closed")
        channels += channel
    }

    @Synchronized
    fun remove(channel: Channel) { channels -= channel }

    fun close() {
        val pending = synchronized(this) {
            closed = true
            channels.toList().also { channels.clear() }
        }
        pending.forEach { runCatching { it.disconnect() } }
    }
}
