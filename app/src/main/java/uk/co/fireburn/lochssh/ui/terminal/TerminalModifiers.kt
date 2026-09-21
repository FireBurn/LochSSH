package uk.co.fireburn.lochssh.ui.terminal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// Shared by the accessory bar and the soft keyboard, so latching CTRL on the bar
// applies to the next character typed on either.
class TerminalModifiers {
    var ctrl by mutableStateOf(false)
    var alt by mutableStateOf(false)
    var fn by mutableStateOf(false)

    val any: Boolean get() = ctrl || alt || fn

    fun clear() {
        ctrl = false
        alt = false
        fn = false
    }
}
