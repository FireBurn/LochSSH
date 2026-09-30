package uk.co.fireburn.lochssh.ui.hosts

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import uk.co.fireburn.lochssh.MainActivity
import uk.co.fireburn.lochssh.R
import uk.co.fireburn.lochssh.data.db.SshHostEntity

object HostShortcuts {
    private const val ACTION = "uk.co.fireburn.lochssh.OPEN_HOST"
    private const val HOST_ID = "shortcut_host_id"

    fun hostId(intent: Intent?): Long? {
        if (intent?.action != ACTION) return null
        return try { intent.getLongExtra(HOST_ID, -1).takeIf { it > 0 } }
        catch (_: RuntimeException) { null }
    }

    fun intent(context: Context, id: Long): Intent {
        require(id > 0)
        return Intent(context, MainActivity::class.java).setAction(ACTION)
            .putExtra(HOST_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    fun pin(context: Context, host: SshHostEntity): String? = try {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            "This launcher does not support pinned shortcuts"
        } else {
            val shortcut = ShortcutInfoCompat.Builder(context, "host-${host.id}")
                .setShortLabel(host.name.ifBlank { host.host }.ifBlank { "SSH host" }.take(40))
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(intent(context, host.id))
                .build()
            if (ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)) null
            else "The launcher could not add the shortcut"
        }
    } catch (e: Exception) {
        "Could not add shortcut: ${e.message ?: "launcher error"}"
    }
}
