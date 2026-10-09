package uk.co.fireburn.lochssh.local

import java.io.ByteArrayOutputStream

// A shell on pipes has no terminal driver, so typing is echoed and edited here and
// a line is sent only when Enter is pressed.
class LineDiscipline {
    class Result(val echo: ByteArray, val toShell: ByteArray, val interrupt: Boolean, val endOfInput: Boolean)

    private val line = ArrayList<ByteArray>()
    private val partial = ByteArrayOutputStream()
    private var escape = Escape.NONE

    private enum class Escape { NONE, START, SEQUENCE, SINGLE }

    fun feed(input: ByteArray): Result {
        val echo = ByteArrayOutputStream()
        val toShell = ByteArrayOutputStream()
        var interrupt = false
        var endOfInput = false
        for (byte in input) {
            val value = byte.toInt() and 0xFF
            when {
                escape != Escape.NONE -> skipEscape(value)
                value == ESC -> escape = Escape.START
                value == CR || value == LF -> {
                    for (piece in line) toShell.write(piece)
                    toShell.write(LF)
                    line.clear()
                    echo.write(CRLF)
                }
                value == BACKSPACE || value == DELETE -> eraseOne(echo)
                value == CTRL_U -> while (line.isNotEmpty()) eraseOne(echo)
                value == CTRL_C -> {
                    line.clear()
                    echo.write("^C\r\n".toByteArray())
                    interrupt = true
                }
                value == CTRL_D -> if (line.isEmpty()) endOfInput = true
                value < SPACE -> Unit
                else -> collect(byte, echo)
            }
        }
        return Result(echo.toByteArray(), toShell.toByteArray(), interrupt, endOfInput)
    }

    private fun skipEscape(value: Int) {
        escape = when (escape) {
            Escape.START -> when (value) {
                '['.code -> Escape.SEQUENCE
                'O'.code -> Escape.SINGLE
                else -> Escape.NONE
            }
            Escape.SEQUENCE -> if (value in 0x40..0x7E) Escape.NONE else Escape.SEQUENCE
            else -> Escape.NONE
        }
    }

    private fun collect(byte: Byte, echo: ByteArrayOutputStream) {
        partial.write(byte.toInt())
        val bytes = partial.toByteArray()
        if (bytes.size < utf8Length(bytes[0].toInt() and 0xFF)) return
        partial.reset()
        line.add(bytes)
        echo.write(bytes)
    }

    private fun utf8Length(first: Int) = when {
        first >= 0xF0 -> 4
        first >= 0xE0 -> 3
        first >= 0xC0 -> 2
        else -> 1
    }

    private fun eraseOne(echo: ByteArrayOutputStream) {
        if (line.isEmpty()) return
        line.removeAt(line.size - 1)
        echo.write("\b \b".toByteArray())
    }

    private companion object {
        const val ESC = 0x1B
        const val CR = 0x0D
        const val LF = 0x0A
        const val BACKSPACE = 0x08
        const val DELETE = 0x7F
        const val CTRL_C = 0x03
        const val CTRL_D = 0x04
        const val CTRL_U = 0x15
        const val SPACE = 0x20
        val CRLF = "\r\n".toByteArray()
    }
}
