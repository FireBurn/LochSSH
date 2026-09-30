package uk.co.fireburn.lochssh.ssh

internal class ForwardWarnings {
    private val messages = ArrayDeque<String>()
    private val listeners = mutableMapOf<Any, (String) -> Unit>()

    @Synchronized
    fun listen(listener: (String) -> Unit): AutoCloseable {
        val token = Any()
        listeners[token] = listener
        messages.forEach(listener)
        return AutoCloseable { synchronized(this) { listeners.remove(token) } }
    }

    @Synchronized
    fun report(message: String) {
        if (messages.lastOrNull() != message) {
            messages.addLast(message)
            if (messages.size > 5) messages.removeFirst()
        }
        listeners.values.toList().forEach { it(message) }
    }
}
