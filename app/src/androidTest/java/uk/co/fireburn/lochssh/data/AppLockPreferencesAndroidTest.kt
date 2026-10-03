package uk.co.fireburn.lochssh.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.UUID

class AppLockPreferencesAndroidTest {
    @Test fun defaultsOffSavesReloadsAndResetsInIsolatedSettings() {
        withPreferences { context, _ ->
            val settings = AppPreferences(context)
            assertFalse(settings.appLock.value)
            settings.setAppLock(true)
            assertTrue(settings.appLock.value)
            assertTrue(AppPreferences(context).appLock.value)
            settings.reset()
            assertFalse(settings.appLock.value)
            assertFalse(AppPreferences(context).appLock.value)
        }
    }

    @Test fun failedWritesDoNotReportTheLockAsSaved() {
        withPreferences { context, real ->
            val failing = object : SharedPreferences by real {
                override fun edit(): SharedPreferences.Editor {
                    val editor = real.edit()
                    return object : SharedPreferences.Editor by editor {
                        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
                            editor.putBoolean(key, value)
                            return this
                        }
                        override fun commit() = false
                    }
                }
            }
            val wrapped = object : ContextWrapper(context) {
                override fun getSharedPreferences(name: String, mode: Int) = failing
            }
            val settings = AppPreferences(wrapped)
            assertThrows(IOException::class.java) { settings.setAppLock(true) }
            assertFalse(settings.appLock.value)
            assertFalse(AppPreferences(context).appLock.value)
        }
    }

    private fun withPreferences(test: (Context, SharedPreferences) -> Unit) {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val file = "app_lock_test_${UUID.randomUUID()}"
        val real = base.getSharedPreferences(file, Context.MODE_PRIVATE)
        val wrapped = object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int) = real
        }
        try { test(wrapped, real) } finally { base.deleteSharedPreferences(file) }
    }
}
