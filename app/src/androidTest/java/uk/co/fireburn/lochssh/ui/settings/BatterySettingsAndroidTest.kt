package uk.co.fireburn.lochssh.ui.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatterySettingsAndroidTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun readsTheSystemStateAndDeclaresTheRequestPermission() {
        assertEquals(context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName), BatterySettings.exemption(context))
        assertEquals(PackageManager.PERMISSION_GRANTED,
            context.checkSelfPermission(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS))
    }

    @Test
    fun requestsOnlyThisAppAfterAnExplicitAction() {
        val calls = mutableListOf<Intent>()
        assertNull(BatterySettings.open(context, true) { calls += it })
        assertEquals(1, calls.size)
        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, calls.single().action)
        assertEquals("package:${context.packageName}", calls.single().dataString)
        assertTrue(calls.single().flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun usesBatterySettingsWhenTheRequestIsUnavailable() {
        val calls = mutableListOf<String?>()
        assertNull(BatterySettings.open(context, true) {
            calls += it.action
            if (calls.size == 1) throw ActivityNotFoundException()
        })
        assertEquals(listOf(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), calls)
    }

    @Test
    fun usesAppSettingsWhenBatterySettingsAreBlocked() {
        val calls = mutableListOf<Intent>()
        assertNull(BatterySettings.open(context, false) {
            calls += it
            if (calls.size == 1) throw SecurityException()
        })
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, calls.last().action)
        assertEquals("package:${context.packageName}", calls.last().dataString)
    }

    @Test
    fun reportsAnErrorWhenNoSettingsPageCanBeOpened() {
        var calls = 0
        assertNotNull(BatterySettings.open(context, true) {
            calls++
            throw ActivityNotFoundException()
        })
        assertEquals(3, calls)
    }
}
