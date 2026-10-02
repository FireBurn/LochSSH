package uk.co.fireburn.lochssh.ssh

import com.jcraft.jsch.Channel
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.Session

internal object TerminalChannel {
    fun open(session: Session, command: String?, cols: Int, rows: Int, forwardAgent: Boolean): Channel =
        if (command == null) {
            (session.openChannel("shell") as ChannelShell).apply {
                setAgentForwarding(forwardAgent)
                setPty(true)
                setPtyType("xterm-256color", cols, rows, 0, 0)
                setEnv("TERM", "xterm-256color")
            }
        } else {
            (session.openChannel("exec") as ChannelExec).apply {
                setCommand(command)
                setAgentForwarding(forwardAgent)
                setPty(true)
                setPtyType("xterm-256color", cols, rows, 0, 0)
                setEnv("TERM", "xterm-256color")
            }
        }

    fun resize(channel: Channel, cols: Int, rows: Int) {
        when (channel) {
            is ChannelShell -> channel.setPtySize(cols, rows, 0, 0)
            is ChannelExec -> channel.setPtySize(cols, rows, 0, 0)
        }
    }
}
