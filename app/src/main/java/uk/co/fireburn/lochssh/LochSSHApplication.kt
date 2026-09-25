package uk.co.fireburn.lochssh

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import uk.co.fireburn.lochssh.data.ConnectionHistoryRecorder
import javax.inject.Inject

@HiltAndroidApp
class LochSSHApplication : Application() {
    @Inject lateinit var history: ConnectionHistoryRecorder

    override fun onCreate() {
        super.onCreate()
        history.start()
    }
}
