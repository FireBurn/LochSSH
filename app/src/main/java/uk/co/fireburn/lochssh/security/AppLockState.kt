package uk.co.fireburn.lochssh.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LockStatus(val enabled: Boolean = false, val locked: Boolean = false,
    val waiting: Boolean = false, val error: String? = null)

class AppLockState {
    private val current = MutableStateFlow(LockStatus())
    val status = current.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        if (current.value.enabled == enabled) return
        current.value = LockStatus(enabled, locked = enabled)
    }

    fun background() {
        if (current.value.enabled) current.value = current.value.copy(locked = true)
    }

    fun beginUnlock(): Boolean {
        val value = current.value
        if (!value.enabled || !value.locked || value.waiting) return false
        current.value = value.copy(waiting = true, error = null)
        return true
    }

    fun result(success: Boolean) {
        val value = current.value
        if (!value.waiting) return
        current.value = value.copy(locked = value.enabled && !success, waiting = false)
    }

    fun failed(message: String) {
        val value = current.value
        if (value.enabled) current.value = value.copy(locked = true, waiting = false, error = message)
    }
}
