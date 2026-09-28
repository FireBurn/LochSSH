package uk.co.fireburn.lochssh.ssh

import java.io.InterruptedIOException

internal class ConnectionAttemptControl {
    private val lock = Any()
    @Volatile private var cancelled = false
    private var worker: Thread? = null

    fun run(block: () -> Unit) {
        synchronized(lock) {
            checkCancelled()
            check(worker == null) { "Connection attempt is already running" }
            worker = Thread.currentThread()
        }
        try {
            block()
            checkCancelled()
        } finally {
            synchronized(lock) {
                worker = null
                if (cancelled) Thread.interrupted()
            }
        }
    }

    fun checkCancelled() {
        if (cancelled) throw InterruptedIOException("Connection cancelled")
    }

    fun cancel() {
        synchronized(lock) {
            cancelled = true
            worker?.interrupt()
        }
    }
}
