package uk.co.fireburn.lochssh

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import uk.co.fireburn.lochssh.data.ConnectionHistoryRecorder
import uk.co.fireburn.lochssh.data.SecretRecovery
import javax.inject.Inject

@HiltAndroidApp
class LochSSHApplication : Application() {
    @Inject lateinit var history: ConnectionHistoryRecorder
    @Inject lateinit var secretRecovery: SecretRecovery

    override fun onCreate() {
        super.onCreate()
        history.start()
        secretRecovery.start()
    }
}
