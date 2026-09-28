package uk.co.fireburn.lochssh.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

object BatterySettings {
    fun exemption(context: Context): Boolean? = try {
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName)
    } catch (_: RuntimeException) { null }

    fun open(
        context: Context,
        requestExemption: Boolean,
        launch: (Intent) -> Unit = { context.startActivity(it) }
    ): String? {
        val packageUri = Uri.fromParts("package", context.packageName, null)
        val intents = buildList {
            if (requestExemption) add(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri))
            add(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
        }
        for (intent in intents) {
            try {
                launch(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return null
            } catch (_: RuntimeException) { }
        }
        return "Could not open battery settings. Open Android settings and find LochSSH's battery options."
    }
}
