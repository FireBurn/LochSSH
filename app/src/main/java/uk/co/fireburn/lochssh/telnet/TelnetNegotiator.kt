package uk.co.fireburn.lochssh.telnet

import java.io.ByteArrayOutputStream

// RFC 854 command handling. Feed received bytes; text goes to the terminal and replies
// go back to the server. Only echo, suppress-go-ahead, terminal type and window size are accepted.
class TelnetNegotiator(private val terminalType: String = "xterm-256color") {
    class Result(val text: ByteArray, val replies: ByteArray)

    private enum class State { TEXT, IAC, VERB, SUBNEGOTIATION, SUBNEGOTIATION_IAC }

    private var state = State.TEXT
    private var verb = 0
    private val subnegotiation = ByteArrayOutputStream()
    private val enabledLocally = mutableSetOf<Int>()
    private val enabledRemotely = mutableSetOf<Int>()
    private var cols = 80
    private var rows = 24

    fun receive(bytes: ByteArray, length: Int = bytes.size): Result {
        val text = ByteArrayOutputStream()
        val replies = ByteArrayOutputStream()
        for (i in 0 until length) step(bytes[i].toInt() and 0xFF, text, replies)
        return Result(text.toByteArray(), replies.toByteArray())
    }

    fun windowSize(newCols: Int, newRows: Int): ByteArray {
        cols = newCols.coerceIn(1, 0xFFFF)
        rows = newRows.coerceIn(1, 0xFFFF)
        return if (NAWS in enabledLocally) sizeMessage() else ByteArray(0)
    }

    private fun step(b: Int, text: ByteArrayOutputStream, replies: ByteArrayOutputStream) {
        when (state) {
            State.TEXT -> if (b == IAC) state = State.IAC else text.write(b)
            State.IAC -> when (b) {
                IAC -> { text.write(IAC); state = State.TEXT }
                WILL, WONT, DO, DONT -> { verb = b; state = State.VERB }
                SB -> { subnegotiation.reset(); state = State.SUBNEGOTIATION }
                else -> state = State.TEXT
            }
            State.VERB -> { answer(verb, b, replies); state = State.TEXT }
            State.SUBNEGOTIATION -> if (b == IAC) state = State.SUBNEGOTIATION_IAC else collect(b)
            State.SUBNEGOTIATION_IAC -> when (b) {
                SE -> { finishSubnegotiation(replies); state = State.TEXT }
                IAC -> { collect(IAC); state = State.SUBNEGOTIATION }
                else -> state = State.TEXT
            }
        }
    }

    private fun collect(b: Int) {
        if (subnegotiation.size() < MAX_SUBNEGOTIATION) subnegotiation.write(b)
    }

    private fun answer(command: Int, option: Int, replies: ByteArrayOutputStream) {
        when (command) {
            WILL -> if (option in REMOTE_OPTIONS) {
                if (enabledRemotely.add(option)) send(replies, DO, option)
            } else send(replies, DONT, option)
            DO -> if (option in LOCAL_OPTIONS) {
                if (enabledLocally.add(option)) {
                    send(replies, WILL, option)
                    if (option == NAWS) replies.write(sizeMessage())
                }
            } else send(replies, WONT, option)
            WONT -> if (enabledRemotely.remove(option)) send(replies, DONT, option)
            DONT -> if (enabledLocally.remove(option)) send(replies, WONT, option)
        }
    }

    private fun finishSubnegotiation(replies: ByteArrayOutputStream) {
        val data = subnegotiation.toByteArray()
        if (data.size >= 2 && data[0].toInt() == TTYPE && data[1].toInt() == SEND && TTYPE in enabledLocally) {
            replies.write(byteArrayOf(IAC.toByte(), SB.toByte(), TTYPE.toByte(), IS.toByte()))
            replies.write(terminalType.toByteArray(Charsets.US_ASCII))
            replies.write(byteArrayOf(IAC.toByte(), SE.toByte()))
        }
    }

    private fun sizeMessage(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(IAC.toByte(), SB.toByte(), NAWS.toByte()))
        for (value in listOf(cols shr 8, cols and 0xFF, rows shr 8, rows and 0xFF)) {
            out.write(value)
            if (value == IAC) out.write(IAC)
        }
        out.write(byteArrayOf(IAC.toByte(), SE.toByte()))
        return out.toByteArray()
    }

    private fun send(out: ByteArrayOutputStream, command: Int, option: Int) {
        out.write(byteArrayOf(IAC.toByte(), command.toByte(), option.toByte()))
    }

    companion object {
        fun escape(bytes: ByteArray): ByteArray {
            val out = ByteArrayOutputStream(bytes.size)
            for (b in bytes) {
                out.write(b.toInt())
                if (b.toInt() and 0xFF == IAC) out.write(IAC)
            }
            return out.toByteArray()
        }

        private const val IAC = 255
        private const val DONT = 254
        private const val DO = 253
        private const val WONT = 252
        private const val WILL = 251
        private const val SB = 250
        private const val SE = 240
        private const val ECHO = 1
        private const val SUPPRESS_GO_AHEAD = 3
        private const val TTYPE = 24
        private const val NAWS = 31
        private const val IS = 0
        private const val SEND = 1
        private const val MAX_SUBNEGOTIATION = 256
        private val REMOTE_OPTIONS = setOf(ECHO, SUPPRESS_GO_AHEAD)
        private val LOCAL_OPTIONS = setOf(SUPPRESS_GO_AHEAD, TTYPE, NAWS)
    }
}
