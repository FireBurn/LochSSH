package uk.co.fireburn.lochssh.security

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.view.Window
import android.view.WindowManager

object DeviceUnlock {
    fun available(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    @Suppress("DEPRECATION")
    fun request(context: Context): Intent =
        context.getSystemService(KeyguardManager::class.java)
            ?.createConfirmDeviceCredentialIntent("Unlock LochSSH", "Confirm your device screen lock to open LochSSH")
            ?: error("Set a device screen lock in Android settings, then try again")

    fun protect(window: Window, enabled: Boolean) {
        if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
