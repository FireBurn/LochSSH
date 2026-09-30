package uk.co.fireburn.lochssh.ssh

import com.jcraft.jsch.Channel
import java.util.concurrent.CopyOnWriteArrayList

internal class ConnectingChannels {
    private val channels = CopyOnWriteArrayList<Channel>()

    fun add(channel: Channel) { channels += channel }
    fun clear() { channels.clear() }

    fun close() {
        channels.toList().asReversed().forEach { runCatching { it.disconnect() } }
    }
}
