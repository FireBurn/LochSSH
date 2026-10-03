package uk.co.fireburn.lochssh.security

import androidx.lifecycle.ViewModel

class AppLockViewModel : ViewModel() {
    val lock = AppLockState()
}
