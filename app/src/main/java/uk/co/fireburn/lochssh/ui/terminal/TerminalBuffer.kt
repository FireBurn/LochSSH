package uk.co.fireburn.lochssh.ui.terminal

// Character grid with cursor and a VT100/xterm parser.
class TerminalBuffer(
    var cols: Int,
    var rows: Int
) {
    data class Cell(val ch: Char, val fg: Int, val bg: Int, val bold: Boolean)

    private var grid = blankGrid(cols, rows)
    private var savedGrid: Array<Array<Cell>>? = null
    private var fg = DEFAULT_FG
    private var bg = DEFAULT_BG
    private var bold = false
    private var inverse = false

    var cursorCol = 0
        private set
    var cursorRow = 0
        private set
    var cursorVisible = true
        private set
    var version = 0
        private set

    val cursor: Pair<Int, Int>
        get() = cursorRow to cursorCol

    // Rows the remote scrolls between, set by the program that is running.
    private var scrollTop = 0
    private var scrollBottom = rows - 1

    private var savedCursor: Triple<Int, Int, Boolean>? = null

    // Parse state
    private var inEscape = false
    private var inCsi = false
    private var inOsc = false
    private var skipNext = false
    private var csiPrivate = false
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
            grid = copyInto(grid, newCols, newRows)
            savedGrid = savedGrid?.let { copyInto(it, newCols, newRows) }
            cols = newCols
            rows = newRows
            scrollTop = scrollTop.coerceIn(0, rows - 1)
            scrollBottom = scrollBottom.coerceIn(scrollTop, rows - 1)
            cursorRow = cursorRow.coerceIn(0, newRows - 1)
            cursorCol = cursorCol.coerceIn(0, newCols - 1)
            version++
        }
    }

    private fun copyInto(source: Array<Array<Cell>>, newCols: Int, newRows: Int):
        Array<Array<Cell>> {
        val target = blankGrid(newCols, newRows)
        for (r in 0 until minOf(source.size, newRows)) {
            for (c in 0 until minOf(source[r].size, newCols)) target[r][c] = source[r][c]
        }
        return target
    }

    private fun feedByte(b: Int) {
        when {
            skipNext -> skipNext = false
            inOsc -> when {
                b == 0x07 -> inOsc = false
                b == 0x1B -> {
                    inOsc = false
                    inEscape = true
                }
                else -> Unit
            }
            inCsi -> when {
                b == '?'.code && csiParams.isEmpty() -> csiPrivate = true
                b in 0x40..0x7E -> {
                    handleCsi(b.toChar())
                    inCsi = false
                    csiPrivate = false
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
                    '7'.code -> saveCursor()
                    '8'.code -> restoreCursor()
                    'D'.code -> lineFeed()
                    'E'.code -> {
                        cursorCol = 0
                        lineFeed()
                    }
                    'M'.code -> reverseIndex()
                    // Character set selection takes one more byte.
                    '('.code, ')'.code, '*'.code, '+'.code -> skipNext = true
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
            0x0A, 0x0B, 0x0C -> lineFeed()
            0x0D -> cursorCol = 0
            in 0x00..0x1F -> Unit
            else -> printable(b)
        }
    }

    private fun printable(b: Int) {
        when {
            utf8Remaining > 0 -> {
                if (b and 0xC0 != 0x80) {
                    utf8Remaining = 0
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
            b and 0xF8 == 0xF0 -> {
                utf8Code = b and 0x07
                utf8Remaining = 3
            }
            else -> putChar('?')
        }
    }

    private fun putChar(c: Char) {
        if (cursorCol >= cols) {
            cursorCol = 0
            lineFeed()
        }
        grid[cursorRow][cursorCol] = cell(c)
        cursorCol++
    }

    private fun cell(c: Char) =
        if (inverse) Cell(c, bg, fg, bold) else Cell(c, fg, bg, bold)

    private fun blank() = Cell(' ', DEFAULT_FG, DEFAULT_BG, false)

    private fun lineFeed() {
        when {
            cursorRow == scrollBottom -> scrollUp(1)
            cursorRow < rows - 1 -> cursorRow++
        }
    }

    private fun reverseIndex() {
        when {
            cursorRow == scrollTop -> scrollDown(1)
            cursorRow > 0 -> cursorRow--
        }
    }

    private fun scrollUp(count: Int) {
        repeat(count.coerceAtLeast(1)) {
            for (r in scrollTop until scrollBottom) grid[r] = grid[r + 1]
            grid[scrollBottom] = blankRow()
        }
    }

    private fun scrollDown(count: Int) {
        repeat(count.coerceAtLeast(1)) {
            for (r in scrollBottom downTo scrollTop + 1) grid[r] = grid[r - 1]
            grid[scrollTop] = blankRow()
        }
    }

    private fun handleCsi(final: Char) {
        val parts = csiParams.toString().split(';').map { it.toIntOrNull() ?: 0 }
        val first = parts.getOrNull(0) ?: 0
        val count = first.coerceAtLeast(1)

        if (csiPrivate) {
            handlePrivateMode(parts, final)
            return
        }

        when (final) {
            'm' -> applySgr(parts)
            'H', 'f' -> {
                cursorRow = (parts.getOrNull(0) ?: 1).coerceAtLeast(1).coerceAtMost(rows) - 1
                cursorCol = (parts.getOrNull(1) ?: 1).coerceAtLeast(1).coerceAtMost(cols) - 1
            }
            'A' -> cursorRow = (cursorRow - count).coerceAtLeast(0)
            'B' -> cursorRow = (cursorRow + count).coerceAtMost(rows - 1)
            'C' -> cursorCol = (cursorCol + count).coerceAtMost(cols - 1)
            'D' -> cursorCol = (cursorCol - count).coerceAtLeast(0)
            'E' -> {
                cursorRow = (cursorRow + count).coerceAtMost(rows - 1)
                cursorCol = 0
            }
            'F' -> {
                cursorRow = (cursorRow - count).coerceAtLeast(0)
                cursorCol = 0
            }
            'G', '`' -> cursorCol = first.coerceAtLeast(1).coerceAtMost(cols) - 1
            'd' -> cursorRow = first.coerceAtLeast(1).coerceAtMost(rows) - 1
            'J' -> clearDisplay(first)
            'K' -> clearLine(first)
            'L' -> insertLines(count)
            'M' -> deleteLines(count)
            'P' -> deleteChars(count)
            '@' -> insertChars(count)
            'X' -> eraseChars(count)
            'S' -> scrollUp(count)
            'T' -> scrollDown(count)
            'r' -> {
                val top = (parts.getOrNull(0) ?: 1).coerceAtLeast(1) - 1
                val bottom = (parts.getOrNull(1)?.takeIf { it > 0 } ?: rows) - 1
                scrollTop = top.coerceIn(0, rows - 1)
                scrollBottom = bottom.coerceIn(scrollTop, rows - 1)
                cursorRow = scrollTop
                cursorCol = 0
            }
            's' -> saveCursor()
            'u' -> restoreCursor()
            else -> Unit
        }
    }

    private fun handlePrivateMode(parts: List<Int>, final: Char) {
        if (final != 'h' && final != 'l') return
        val enable = final == 'h'
        when (parts.getOrNull(0)) {
            25 -> cursorVisible = enable
            // Full screen programs swap to their own screen and back.
            47, 1047, 1049 -> if (enable) enterAlternateScreen() else leaveAlternateScreen()
        }
    }

    private fun enterAlternateScreen() {
        if (savedGrid != null) return
        saveCursor()
        savedGrid = grid
        grid = blankGrid(cols, rows)
        cursorRow = 0
        cursorCol = 0
    }

    private fun leaveAlternateScreen() {
        val restored = savedGrid ?: return
        grid = restored
        savedGrid = null
        restoreCursor()
    }

    private fun saveCursor() {
        savedCursor = Triple(cursorRow, cursorCol, bold)
    }

    private fun restoreCursor() {
        val saved = savedCursor ?: return
        cursorRow = saved.first.coerceIn(0, rows - 1)
        cursorCol = saved.second.coerceIn(0, cols - 1)
        bold = saved.third
    }

    private fun deleteChars(count: Int) {
        val row = grid[cursorRow]
        val from = cursorCol.coerceIn(0, cols - 1)
        val n = count.coerceAtMost(cols - from)
        for (c in from until cols - n) row[c] = row[c + n]
        for (c in cols - n until cols) row[c] = blank()
    }

    private fun insertChars(count: Int) {
        val row = grid[cursorRow]
        val from = cursorCol.coerceIn(0, cols - 1)
        val n = count.coerceAtMost(cols - from)
        for (c in cols - 1 downTo from + n) row[c] = row[c - n]
        for (c in from until from + n) row[c] = blank()
    }

    private fun eraseChars(count: Int) {
        val row = grid[cursorRow]
        val from = cursorCol.coerceIn(0, cols - 1)
        for (c in from until (from + count).coerceAtMost(cols)) row[c] = blank()
    }

    private fun insertLines(count: Int) {
        if (cursorRow !in scrollTop..scrollBottom) return
        repeat(count.coerceAtMost(scrollBottom - cursorRow + 1)) {
            for (r in scrollBottom downTo cursorRow + 1) grid[r] = grid[r - 1]
            grid[cursorRow] = blankRow()
        }
    }

    private fun deleteLines(count: Int) {
        if (cursorRow !in scrollTop..scrollBottom) return
        repeat(count.coerceAtMost(scrollBottom - cursorRow + 1)) {
            for (r in cursorRow until scrollBottom) grid[r] = grid[r + 1]
            grid[scrollBottom] = blankRow()
        }
    }

    private fun applySgr(parts: List<Int>) {
        val list = if (parts.isEmpty()) listOf(0) else parts
        var i = 0
        while (i < list.size) {
            when (val p = list[i]) {
                0 -> {
                    fg = DEFAULT_FG
                    bg = DEFAULT_BG
                    bold = false
                    inverse = false
                }
                1 -> bold = true
                7 -> inverse = true
                22 -> bold = false
                27 -> inverse = false
                in 30..37 -> fg = ANSI_COLORS[p - 30]
                39 -> fg = DEFAULT_FG
                in 40..47 -> bg = ANSI_COLORS[p - 40]
                49 -> bg = DEFAULT_BG
                in 90..97 -> fg = BRIGHT_COLORS[p - 90]
                in 100..107 -> bg = BRIGHT_COLORS[p - 100]
                38, 48 -> {
                    val target = p
                    when (list.getOrNull(i + 1)) {
                        5 -> {
                            val colour = color256(list.getOrNull(i + 2) ?: 0)
                            if (target == 38) fg = colour else bg = colour
                            i += 2
                        }
                        2 -> {
                            val r = list.getOrNull(i + 2) ?: 0
                            val g = list.getOrNull(i + 3) ?: 0
                            val b = list.getOrNull(i + 4) ?: 0
                            val colour = 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
                            if (target == 38) fg = colour else bg = colour
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
            0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
        }
        else -> {
            val v = 8 + (n - 232) * 10
            0xFF000000.toInt() or (v shl 16) or (v shl 8) or v
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
                clearLine(1)
            }
            else -> {
                for (r in 0 until rows) grid[r] = blankRow()
            }
        }
    }

    private fun clearLine(mode: Int) {
        when (mode) {
            0 -> for (c in cursorCol.coerceIn(0, cols) until cols) grid[cursorRow][c] = blank()
            1 -> for (c in 0..cursorCol.coerceIn(0, cols - 1)) grid[cursorRow][c] = blank()
            else -> for (c in 0 until cols) grid[cursorRow][c] = blank()
        }
    }

    private fun blankRow() = Array(cols) { blank() }

    companion object {
        const val DEFAULT_FG = 0xFFE0E1DD.toInt()
        const val DEFAULT_BG = 0

        val ANSI_COLORS = intArrayOf(
            0xFF000000.toInt(), 0xFFCC0000.toInt(), 0xFF00CC00.toInt(), 0xFFCCCC00.toInt(),
            0xFF0000EE.toInt(), 0xFFCC00CC.toInt(), 0xFF00CCCC.toInt(), 0xFFEEEEEE.toInt()
        )

        val BRIGHT_COLORS = intArrayOf(
            0xFF777777.toInt(), 0xFFFF5555.toInt(), 0xFF55FF55.toInt(), 0xFFFFFF55.toInt(),
            0xFF5555FF.toInt(), 0xFFFF55FF.toInt(), 0xFF55FFFF.toInt(), 0xFFFFFFFF.toInt()
        )

        private fun blankGrid(cols: Int, rows: Int) =
            Array(rows) { Array(cols) { Cell(' ', DEFAULT_FG, DEFAULT_BG, false) } }
    }
}
