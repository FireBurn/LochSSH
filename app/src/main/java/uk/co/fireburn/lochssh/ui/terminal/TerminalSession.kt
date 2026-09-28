package uk.co.fireburn.lochssh.ui.terminal

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TerminalSession(cols: Int, rows: Int) {
    val buffer = TerminalBuffer(cols, rows)
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision

    fun feed(bytes: ByteArray, length: Int) = buffer.read {
        buffer.feed(bytes, length)
        _revision.value++
    }
}
