package uk.co.fireburn.lochssh.ssh

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.fireburn.lochssh.data.db.RemoteSessionModes

class SshTransportSettingsTest {
    private val config = SshConnectionConfig("host", 22, "user", "PASSWORD", "secret", null, null, null, 30)

    @Test fun discoveryAndAttachChannelsCanShareTheSameTransport() {
        assertTrue(config.copy(remoteSessionMode = RemoteSessionModes.CONNECT_ALL).sameTransport(config))
        assertTrue(config.copy(autoCommand = "pwd", remoteSessionMode = RemoteSessionModes.TMUX).sameTransport(config))
    }

    @Test fun credentialsProxiesAndForwardsStillRequireSeparateTransports() {
        assertFalse(config.copy(password = "changed").sameTransport(config))
        assertFalse(config.copy(proxy = SshProxySettings(SshProxyType.HTTP, "proxy", 8080)).sameTransport(config))
        assertFalse(config.copy(forwards = listOf(PortForwardSpec("LOCAL", 8080, "localhost", 80))).sameTransport(config))
        assertFalse(config.copy(agentForwarding = true).sameTransport(config))
    }
}
