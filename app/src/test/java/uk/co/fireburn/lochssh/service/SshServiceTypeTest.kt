package uk.co.fireburn.lochssh.service

import android.content.pm.ServiceInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class SshServiceTypeTest {
    @Test
    fun usesTheDeclaredSshTypeOnRecentAndroid() {
        listOf(34, 35, 36).forEach {
            assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE, SshServiceType.forAndroid(it))
        }
    }

    @Test
    fun keepsTheOlderServiceStartCompatible() {
        listOf(29, 30, 33).forEach {
            assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, SshServiceType.forAndroid(it))
        }
        listOf(26, 28).forEach {
            assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE, SshServiceType.forAndroid(it))
        }
    }
}
