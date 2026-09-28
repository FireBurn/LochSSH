package uk.co.fireburn.lochssh.ssh

object KeepAlive {
    const val MAX_SECONDS = Int.MAX_VALUE / 1000

    fun parse(text: String): Int? = text.toIntOrNull()?.takeIf { it in 0..MAX_SECONDS }

    fun milliseconds(seconds: Int): Int {
        require(seconds in 0..MAX_SECONDS) { "Keep-alive must be between 0 and $MAX_SECONDS seconds" }
        return seconds * 1000
    }
}
