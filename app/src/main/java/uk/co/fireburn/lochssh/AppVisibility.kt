package uk.co.fireburn.lochssh

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Whether the app is on screen. Background work that only matters to someone looking at it waits on this.
object AppVisibility {
    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible

    fun set(value: Boolean) { _visible.value = value }
}
