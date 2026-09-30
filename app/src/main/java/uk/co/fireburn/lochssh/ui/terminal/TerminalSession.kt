package uk.co.fireburn.lochssh.ui.terminal

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TerminalSession(cols: Int, rows: Int, onReply: (String) -> Unit = {}) {
    val buffer = TerminalBuffer(cols, rows, onReply)
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision

    fun feed(bytes: ByteArray, length: Int) = buffer.read {
        buffer.feed(bytes, length)
        _revision.value++
    }
}
