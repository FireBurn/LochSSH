package uk.co.fireburn.lochssh.service

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class SshServiceDeclarationAndroidTest {
    @Test
    fun declaresTheSelectedTypeAndItsPermission() {
        assumeTrue(Build.VERSION.SDK_INT >= 34)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getServiceInfo(ComponentName(context, SshForegroundService::class.java), 0)
        val selected = SshServiceType.forAndroid(Build.VERSION.SDK_INT)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE, selected)
        assertTrue(info.foregroundServiceType and selected == selected)
        assertEquals(PackageManager.PERMISSION_GRANTED,
            context.checkSelfPermission(Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE))
        val purpose = context.packageManager.getProperty(
            "android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE", ComponentName(context, SshForegroundService::class.java))
        assertTrue(purpose.string?.contains("interactive SSH") == true)
    }
}
