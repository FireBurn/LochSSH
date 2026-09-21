package uk.co.fireburn.lochssh.ui.terminal

// Character grid with cursor and a small ANSI/SGR parser.
class TerminalBuffer(
    var cols: Int,
    var rows: Int
) {
    data class Cell(val ch: Char, val fg: Int, val bold: Boolean)

    private var grid = Array(rows) { Array(cols) { Cell(' ', DEFAULT_FG, false) } }
    private var fg = DEFAULT_FG
    private var bold = false

    var cursorCol = 0
        private set
    var cursorRow = 0
        private set
    var version = 0
        private set

    val cursor: Pair<Int, Int>
        get() = cursorRow to cursorCol

    // ANSI parse state
    private var inEscape = false
    private var inCsi = false
    private var inOsc = false
    private val csiParams = StringBuilder()

    // UTF-8 decode state
    private var utf8Remaining = 0
    private var utf8Code = 0

    // The reader thread feeds while the UI thread draws, so both sides take
    // this lock; readers take it once per frame through read().
    private val lock = Any()

    fun <R> read(block: () -> R): R = synchronized(lock) { block() }

    fun feed(bytes: ByteArray, length: Int) {
        synchronized(lock) {
            for (i in 0 until length) feedByte(bytes[i].toInt() and 0xFF)
            version++
        }
    }

    fun cellAt(row: Int, col: Int): Cell = grid[row][col]

    fun rowText(row: Int, start: Int, end: Int): String {
        val sb = StringBuilder(end - start)
        for (c in start until end) sb.append(grid[row][c].ch)
        return sb.toString()
    }

    fun resize(newCols: Int, newRows: Int) {
        if (newCols <= 0 || newRows <= 0) return
        synchronized(lock) {
            if (newCols == cols && newRows == rows) return
            val newGrid = Array(newRows) { Array(newCols) { Cell(' ', DEFAULT_FG, false) } }
            for (r in 0 until minOf(rows, newRows)) {
                for (c in 0 until minOf(cols, newCols)) newGrid[r][c] = grid[r][c]
            }
            grid = newGrid
            cols = newCols
            rows = newRows
            cursorRow = cursorRow.coerceIn(0, newRows - 1)
            cursorCol = cursorCol.coerceIn(0, newCols - 1)
            version++
        }
    }

    private fun feedByte(b: Int) {
        when {
            inOsc -> when {
                b == 0x07 -> inOsc = false
                b == 0x1B -> {
                    inOsc = false
                    inEscape = true
                }
                else -> Unit
            }
            inCsi -> when {
                b in 0x40..0x7E -> {
                    handleCsi(b.toChar())
                    inCsi = false
                    csiParams.clear()
                }
                b >= 0x20 -> csiParams.append(b.toChar())
                else -> Unit
            }
            inEscape -> {
                inEscape = false
                when (b) {
                    '['.code -> inCsi = true
                    ']'.code -> inOsc = true
                    else -> Unit
                }
            }
            b == 0x1B -> inEscape = true
            else -> controlOrPrint(b)
        }
    }

    private fun controlOrPrint(b: Int) {
        when (b) {
            0x07 -> Unit // bell
            0x08 -> cursorCol = (cursorCol - 1).coerceAtLeast(0)
            0x09 -> cursorCol = ((cursorCol / 8 + 1) * 8).coerceAtMost(cols - 1)
            0x0A -> lineFeed()
            0x0D -> cursorCol = 0
            in 0x00..0x1F -> Unit
            else -> printable(b)
        }
    }

    private fun printable(b: Int) {
        when {
            utf8Remaining > 0 -> {
                if (b and 0xC0 != 0x80) {
                    putChar(b.toChar())
                    return
                }
                utf8Code = (utf8Code shl 6) or (b and 0x3F)
                utf8Remaining--
                if (utf8Remaining == 0) putChar(utf8Code.toChar())
            }
            b < 0x80 -> putChar(b.toChar())
            b and 0xE0 == 0xC0 -> {
                utf8Code = b and 0x1F
                utf8Remaining = 1
            }
            b and 0xF0 == 0xE0 -> {
                utf8Code = b and 0x0F
                utf8Remaining = 2
            }
            else -> putChar('?')
        }
    }

    private fun putChar(c: Char) {
        if (cursorCol >= cols) {
            cursorCol = 0
            lineFeed()
        }
        grid[cursorRow][cursorCol] = Cell(c, fg, bold)
        cursorCol++
    }

    private fun lineFeed() {
        cursorRow++
        if (cursorRow >= rows) {
            cursorRow = rows - 1
            scroll()
        }
    }

    private fun scroll() {
        for (r in 0 until rows - 1) grid[r] = grid[r + 1]
        grid[rows - 1] = Array(cols) { Cell(' ', DEFAULT_FG, false) }
    }

    private fun handleCsi(final: Char) {
        val parts = csiParams.toString().split(';').map { it.toIntOrNull() ?: 0 }
        when (final) {
            'm' -> applySgr(parts)
            'H', 'f' -> {
                cursorRow = (parts.getOrNull(0) ?: 1).coerceIn(1, rows) - 1
                cursorCol = (parts.getOrNull(1) ?: 1).coerceIn(1, cols) - 1
            }
            'A' -> cursorRow = (cursorRow - (parts.getOrNull(0) ?: 1)).coerceAtLeast(0)
            'B' -> cursorRow = (cursorRow + (parts.getOrNull(0) ?: 1)).coerceAtMost(rows - 1)
            'C' -> cursorCol = (cursorCol + (parts.getOrNull(0) ?: 1)).coerceAtMost(cols - 1)
            'D' -> cursorCol = (cursorCol - (parts.getOrNull(0) ?: 1)).coerceAtLeast(0)
            'G' -> cursorCol = (parts.getOrNull(0) ?: 1).coerceIn(1, cols) - 1
            'J' -> clearDisplay(parts.getOrNull(0) ?: 0)
            'K' -> clearLine(parts.getOrNull(0) ?: 0)
            else -> Unit
        }
    }

    private fun applySgr(parts: List<Int>) {
        val list = if (parts.isEmpty()) listOf(0) else parts
        var i = 0
        while (i < list.size) {
            when (val p = list[i]) {
                0 -> {
                    fg = DEFAULT_FG
                    bold = false
                }
                1 -> bold = true
                22 -> bold = false
                in 30..37 -> fg = ANSI_COLORS[p - 30]
                39 -> fg = DEFAULT_FG
                in 90..97 -> fg = BRIGHT_COLORS[p - 90]
                38 -> {
                    val mode = list.getOrNull(i + 1)
                    when (mode) {
                        5 -> {
                            fg = color256(list.getOrNull(i + 2) ?: 0)
                            i += 2
                        }
                        2 -> {
                            val r = list.getOrNull(i + 2) ?: 0
                            val g = list.getOrNull(i + 3) ?: 0
                            val b = list.getOrNull(i + 4) ?: 0
                            fg = (r shl 16) or (g shl 8) or b
                            i += 4
                        }
                    }
                }
                else -> Unit
            }
            i++
        }
    }

    private fun color256(n: Int): Int = when {
        n < 16 -> if (n < 8) ANSI_COLORS[n] else BRIGHT_COLORS[n - 8]
        n < 232 -> {
            fun scale(v: Int) = if (v == 0) 0 else 55 + v * 40
            val r = scale((n - 16) / 36)
            val g = scale(((n - 16) % 36) / 6)
            val b = scale((n - 16) % 6)
            (r shl 16) or (g shl 8) or b
        }
        else -> {
            val v = 8 + (n - 232) * 10
            (v shl 16) or (v shl 8) or v
        }
    }

    private fun clearDisplay(mode: Int) {
        when (mode) {
            0 -> {
                clearLine(0)
                for (r in cursorRow + 1 until rows) grid[r] = blankRow()
            }
            1 -> {
                for (r in 0 until cursorRow) grid[r] = blankRow()
                clearLine(2)
            }
            else -> for (r in 0 until rows) grid[r] = blankRow()
        }
    }

    private fun clearLine(mode: Int) {
        val blank = Cell(' ', DEFAULT_FG, false)
        when (mode) {
            0 -> for (c in cursorCol.coerceIn(0, cols) until cols) grid[cursorRow][c] = blank
            1 -> for (c in 0..cursorCol.coerceIn(0, cols - 1)) grid[cursorRow][c] = blank
            else -> for (c in 0 until cols) grid[cursorRow][c] = blank
        }
    }

    private fun blankRow() = Array(cols) { Cell(' ', DEFAULT_FG, false) }

    companion object {
        const val DEFAULT_FG = 0xFFE0E1DD.toInt()

        val ANSI_COLORS = intArrayOf(
            0xFF000000.toInt(), 0xFFCC0000.toInt(), 0xFF00CC00.toInt(), 0xFFCCCC00.toInt(),
            0xFF0000EE.toInt(), 0xFFCC00CC.toInt(), 0xFF00CCCC.toInt(), 0xFFEEEEEE.toInt()
        )

        val BRIGHT_COLORS = intArrayOf(
            0xFF777777.toInt(), 0xFFFF5555.toInt(), 0xFF55FF55.toInt(), 0xFFFFFF55.toInt(),
            0xFF5555FF.toInt(), 0xFFFF55FF.toInt(), 0xFF55FFFF.toInt(), 0xFFFFFFFF.toInt()
        )
    }
}
