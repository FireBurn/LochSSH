package uk.co.fireburn.lochssh.ui.hosts

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import uk.co.fireburn.lochssh.MainActivity

class HostShortcutsAndroidTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun launchContainsOnlyTheSavedHostId() {
        val intent = HostShortcuts.intent(context, 42)
        assertEquals(42L, HostShortcuts.hostId(intent))
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertEquals(setOf("shortcut_host_id"), intent.extras?.keySet())
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
    }

    @Test
    fun ignoresUnrelatedActionsAndInvalidIds() {
        assertNull(HostShortcuts.hostId(null))
        assertNull(HostShortcuts.hostId(Intent(Intent.ACTION_MAIN)))
        val intent = HostShortcuts.intent(context, 42).setAction(Intent.ACTION_VIEW)
        assertNull(HostShortcuts.hostId(intent))
        assertNull(HostShortcuts.hostId(HostShortcuts.intent(context, 42).putExtra("shortcut_host_id", 0L)))
        assertNull(HostShortcuts.hostId(HostShortcuts.intent(context, 42).putExtra("shortcut_host_id", "wrong type")))
        assertThrows(IllegalArgumentException::class.java) { HostShortcuts.intent(context, 0) }
        assertThrows(IllegalArgumentException::class.java) { HostShortcuts.intent(context, -1) }
    }
}
