package uk.co.fireburn.lochssh.ssh

// Works out what a terminal would put on the tab. Shells either set a window
// title with OSC 0/1/2, or describe themselves with the OSC 3008 shell
// integration, which carries the user, host and working directory.
class WindowTitleScanner(private val onTitle: (String) -> Unit) {

    private enum class State { TEXT, ESCAPE, PARAM, BODY, BODY_ESCAPE }

    private var state = State.TEXT
    private val param = StringBuilder()
    // Kept as bytes: titles carry UTF-8, so they can only be decoded once whole.
    private val body = java.io.ByteArrayOutputStream()

    fun feed(bytes: ByteArray, length: Int) {
        for (i in 0 until length) feed(bytes[i].toInt() and 0xFF)
    }

    private fun feed(b: Int) {
        when (state) {
            State.TEXT -> if (b == ESC) state = State.ESCAPE
            State.ESCAPE -> {
                state = if (b == OSC) State.PARAM else State.TEXT
                param.setLength(0)
                body.reset()
            }
            State.PARAM -> when {
                b == ';'.code -> state = if (param.toString() in HANDLED) State.BODY else State.TEXT
                param.length < 8 -> param.append(b.toChar())
                else -> state = State.TEXT
            }
            State.BODY -> when {
                b == BEL -> finish()
                b == ESC -> state = State.BODY_ESCAPE
                body.size() >= MAX_LENGTH -> state = State.TEXT
                else -> body.write(b)
            }
            // A body can also end with ST, which is ESC followed by a backslash.
            State.BODY_ESCAPE -> if (b == '\\'.code) finish() else state = State.TEXT
        }
    }

    private fun finish() {
        val raw = String(body.toByteArray(), Charsets.UTF_8)
        val value = if (param.toString() == SHELL_INTEGRATION) describe(raw) else raw.trim()
        state = State.TEXT
        if (!value.isNullOrBlank()) onTitle(value)
    }

    // start=...;type=shell;user=fireburn;hostname=axion.example;cwd=/home/fireburn
    private fun describe(body: String): String? {
        val fields = body.split(';')
            .mapNotNull { field ->
                val at = field.indexOf('=')
                if (at <= 0) null else field.substring(0, at) to field.substring(at + 1)
            }
            .toMap()

        val user = fields["user"]
        val host = fields["hostname"]?.substringBefore('.')
        val cwd = fields["cwd"]?.let { path ->
            if (user != null && path.startsWith("/home/$user")) {
                "~" + path.removePrefix("/home/$user")
            } else {
                path
            }
        }

        val who = when {
            user != null && host != null -> "$user@$host"
            else -> host ?: user
        }
        return listOfNotNull(who, cwd).joinToString(": ").ifBlank { null }
    }

    private companion object {
        const val ESC = 0x1B
        const val BEL = 0x07
        const val OSC = ']'.code
        const val MAX_LENGTH = 512
        const val SHELL_INTEGRATION = "3008"
        val HANDLED = setOf("0", "1", "2", SHELL_INTEGRATION)
    }
}
