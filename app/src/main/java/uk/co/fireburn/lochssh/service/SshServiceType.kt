package uk.co.fireburn.lochssh.service

import android.content.pm.ServiceInfo

object SshServiceType {
    fun forAndroid(sdk: Int): Int = when {
        sdk >= 34 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        sdk >= 29 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        else -> ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE
    }
}
