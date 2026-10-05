package uk.co.fireburn.lochssh.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteTasksTest {
    @Test fun includesAgentPaneTitleWithoutRequiringTitlePassthrough() {
        val entry = RemoteSessions.parseTmux(RemoteTasks.enrich(
            "\$0|work\tcodex\tcodex\t100\tLochSSH fixes · waiting|1", "")).single()
        assertEquals("work · codex · LochSSH fixes · waiting", entry.name)
        assertEquals("tmux attach-session -t '\$0'", RemoteSessions.attach(entry))
    }
    private val processes = """
        100 1 100 110 bash
        110 100 110 110 sudo
        120 110 120 130 su
        130 120 130 140 bash
        140 130 140 140 git-update
        150 140 140 140 emerge
        160 150 140 140 cc1plus
        170 100 170 110 background-job
        200 1 200 200 other-host-task
    """.trimIndent()

    @Test fun followsSudoChildrenAcrossDifferentForegroundGroups() {
        assertEquals("git-update · emerge · cc1plus", RemoteTasks.names(processes, 100))
    }

    @Test fun absentOrHiddenChildrenDoNotInventATask() {
        assertNull(RemoteTasks.names("100 1 100 110 bash\n110 100 110 110 sudo", 100))
        assertNull(RemoteTasks.names(processes, 999))
        assertNull(RemoteTasks.names("bad input", 100))
    }

    @Test fun enrichesNamesWithoutDisplayingProcessIds() {
        val output = RemoteTasks.enrich("\$0|work\tsudo\tbuild\t100|1", processes)
        assertEquals("work · git-update · emerge · cc1plus · build", RemoteSessions.parseTmux(output).single().name)
        assertEquals("work · sudo · build", RemoteSessions.parseTmux(RemoteTasks.enrich("\$0|work\tsudo\tbuild\t100|1", "")).single().name)
    }

    @Test fun notificationsKeepHostSessionAndLiveTitle() {
        val session = SessionRegistry.Session(42, 1, "Quark", remoteName = "work · sudo", title = "emerge")
        assertEquals("Quark · work · sudo · emerge · #42", session.notificationTitle)
        assertEquals("Quark · #43", SessionRegistry.Session(43, 1, "Quark", title = "Quark").notificationTitle)
    }
}
