package uk.co.fireburn.lochssh.ui.terminal

internal class TerminalUtf8(private val emit: (Int) -> Unit) {
    private var remaining = 0
    private var value = 0
    private var lower = 0x80
    private var upper = 0xBF

    fun feed(byte: Int) {
        if (remaining > 0) {
            if (byte !in lower..upper) {
                finish()
                feed(byte)
                return
            }
            value = (value shl 6) or (byte and 0x3F)
            remaining--
            lower = 0x80
            upper = 0xBF
            if (remaining == 0) emit(value)
            return
        }
        when (byte) {
            in 0..0x7F -> emit(byte)
            in 0xC2..0xDF -> start(byte and 0x1F, 1)
            in 0xE0..0xEF -> {
                start(byte and 0x0F, 2)
                if (byte == 0xE0) lower = 0xA0
                if (byte == 0xED) upper = 0x9F
            }
            in 0xF0..0xF4 -> {
                start(byte and 0x07, 3)
                if (byte == 0xF0) lower = 0x90
                if (byte == 0xF4) upper = 0x8F
            }
            else -> emit(0xFFFD)
        }
    }

    fun finish() {
        val incomplete = remaining > 0
        reset()
        if (incomplete) emit(0xFFFD)
    }

    fun reset() {
        remaining = 0
        value = 0
        lower = 0x80
        upper = 0xBF
    }

    private fun start(code: Int, count: Int) {
        value = code
        remaining = count
    }
}
