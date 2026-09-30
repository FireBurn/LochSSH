package uk.co.fireburn.lochssh.ui.terminal

// Character grid with cursor and a VT100/xterm parser.
class TerminalBuffer(
    var cols: Int,
    var rows: Int,
    private val onReply: (String) -> Unit = {}
) {
    data class Cell(val text: String, val fg: Int, val bg: Int, val bold: Boolean)
    data class Point(val row: Int, val col: Int)

    private var grid = blankGrid(cols, rows)
    private val history = ArrayDeque<Array<Cell>>()
    private var savedGrid: Array<Array<Cell>>? = null
    private var fg = DEFAULT_FG
    private var bg = DEFAULT_BG
    private var bold = false
    private var inverse = false
    private var wraparound = true
    private var originMode = false
    private val charsets = TerminalCharsets()
    private var lastWritten: Point? = null

    var cursorCol = 0
        private set
    var cursorRow = 0
        private set
    var cursorVisible = true
        private set
    var bracketedPaste = false
        private set
    var applicationCursor = false
        private set
    var version = 0
        private set
    var totalScrolled = 0L
        private set

    val scrollbackSize: Int
        get() = history.size

    val cursor: Pair<Int, Int>
        get() = cursorRow to cursorCol

    // Rows the remote scrolls between, set by the program that is running.
    private var scrollTop = 0
    private var scrollBottom = rows - 1

    private data class SavedCursor(
        val row: Int, val col: Int, val fg: Int, val bg: Int,
        val bold: Boolean, val inverse: Boolean, val origin: Boolean, val wrap: Boolean,
        val charsets: TerminalCharsets.State
    )
    private var savedCursor: SavedCursor? = null
    private data class AlternateState(
        val row: Int,
        val col: Int,
        val top: Int,
        val bottom: Int,
        val savedCursor: SavedCursor?,
        val origin: Boolean,
        val charsets: TerminalCharsets.State,
        val lastWritten: Point?
    )
    private var alternateState: AlternateState? = null

    // Parse state
    private var inEscape = false
    private var inCsi = false
    private var inOsc = false
    private var charsetSlot: Int? = null
    private var csiPrivate = false
    private val csiParams = StringBuilder()
    private var csiOverflow = false

    private val utf8 = TerminalUtf8(::putCodePoint)

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

    fun transcript(): String = read {
        val lines = if (savedGrid == null) history.toList() + grid.toList() else grid.toList()
        lines.joinToString("\n") { line -> line.joinToString("") { it.text }.trimEnd() }
            .trimEnd('\n') + "\n"
    }

    fun visibleCellAt(row: Int, col: Int, scrollOffset: Int): Cell {
        val index = history.size - scrollOffset.coerceIn(0, history.size) + row
        val line = if (index < history.size) history[index] else grid[index - history.size]
        return line[col]
    }

    fun visibleRowText(row: Int, start: Int, end: Int, scrollOffset: Int): String {
        val sb = StringBuilder(end - start)
        for (c in start until end) sb.append(visibleCellAt(row, c, scrollOffset).text)
        return sb.toString()
    }

    fun selectedText(start: Point, end: Point, scrollOffset: Int): String {
        val safeStart = Point(start.row.coerceIn(0, rows - 1), start.col.coerceIn(0, cols - 1))
        val safeEnd = Point(end.row.coerceIn(0, rows - 1), end.col.coerceIn(0, cols - 1))
        val first = minOf(safeStart, safeEnd, compareBy(Point::row, Point::col))
        val last = maxOf(safeStart, safeEnd, compareBy(Point::row, Point::col))
        return (first.row..last.row).joinToString("\n") { row ->
            val from = if (row == first.row) first.col else 0
            val until = if (row == last.row) last.col + 1 else cols
            visibleRowText(row, from, until, scrollOffset).trimEnd()
        }
    }

    fun rowText(row: Int, start: Int, end: Int): String {
        val sb = StringBuilder(end - start)
        for (c in start until end) sb.append(grid[row][c].text)
        return sb.toString()
    }

    fun resize(newCols: Int, newRows: Int) {
        if (newCols <= 0 || newRows <= 0) return
        synchronized(lock) {
            if (newCols == cols && newRows == rows) return
            val oldRows = rows
            val oldGrid = grid
            val oldTop = scrollTop
            val oldBottom = scrollBottom
            val start = if (newRows < oldRows) {
                (cursorRow - newRows + 1).coerceIn(0, oldRows - newRows)
            } else 0
            if (savedGrid == null) {
                for (row in 0 until start) appendHistory(oldGrid[row])
            }
            val restored = if (savedGrid == null && newRows > oldRows) {
                List(minOf(newRows - oldRows, history.size)) { history.removeLast() }.asReversed()
            } else emptyList()
            grid = copyInto(oldGrid, newCols, newRows, start, restored)
            val oldSaved = savedGrid
            if (oldSaved != null) {
                val state = alternateState
                val savedStart = if (newRows < oldRows) {
                    ((state?.row ?: 0) - newRows + 1).coerceIn(0, oldRows - newRows)
                } else 0
                savedGrid = copyInto(oldSaved, newCols, newRows, savedStart)
                alternateState = state?.copy(
                    row = (state.row - savedStart).coerceIn(0, newRows - 1),
                    col = state.col.coerceIn(0, newCols - 1),
                    top = (state.top - savedStart).coerceIn(0, newRows - 1),
                    bottom = (state.bottom - savedStart).coerceIn(0, newRows - 1)
                )
            }
            val resizedHistory = history.map { row ->
                Array(newCols) { col -> row.getOrNull(col) ?: blank() }
            }
            history.clear()
            history.addAll(resizedHistory)
            cols = newCols
            rows = newRows
            val rowShift = restored.size - start
            if (oldTop == 0 && oldBottom == oldRows - 1) {
                scrollTop = 0
                scrollBottom = newRows - 1
            } else {
                scrollTop = (oldTop + rowShift).coerceIn(0, newRows - 1)
                scrollBottom = (oldBottom + rowShift).coerceIn(scrollTop, newRows - 1)
            }
            cursorRow = (cursorRow + rowShift).coerceIn(0, newRows - 1)
            if (originMode) cursorRow = cursorRow.coerceIn(scrollTop, scrollBottom)
            cursorCol = cursorCol.coerceIn(0, newCols - 1)
            version++
        }
    }

    private fun copyInto(
        source: Array<Array<Cell>>,
        newCols: Int,
        newRows: Int,
        sourceStart: Int,
        prefix: List<Array<Cell>> = emptyList()
    ):
        Array<Array<Cell>> {
        val target = blankGrid(newCols, newRows)
        val lines = prefix + source.drop(sourceStart)
        for (r in 0 until minOf(lines.size, newRows)) {
            for (c in 0 until minOf(lines[r].size, newCols)) target[r][c] = lines[r][c]
        }
        return target
    }

    private fun feedByte(b: Int) {
        if (b == 0x1B || b == 0x18 || b == 0x1A) {
            clearParser()
            inEscape = b == 0x1B
            return
        }
        when {
            charsetSlot != null -> {
                if (b >= 0x20) {
                    charsets.designate(requireNotNull(charsetSlot), b.toChar())
                    charsetSlot = null
                } else controlOrPrint(b)
            }
            inOsc -> when {
                b == 0x07 -> inOsc = false
                else -> Unit
            }
            inCsi -> when {
                b == '?'.code && csiParams.isEmpty() -> csiPrivate = true
                b in 0x40..0x7E -> {
                    if (!csiOverflow) handleCsi(b.toChar())
                    inCsi = false
                    csiPrivate = false
                    csiParams.clear()
                    csiOverflow = false
                }
                b >= 0x20 -> {
                    if (csiParams.length < 256) csiParams.append(b.toChar())
                    else csiOverflow = true
                }
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
                    'c'.code -> reset(full = true)
                    '('.code, ')'.code, '*'.code, '+'.code -> charsetSlot = b - '('.code
                    'N'.code -> charsets.next(2)
                    'O'.code -> charsets.next(3)
                    'n'.code -> charsets.use(2)
                    'o'.code -> charsets.use(3)
                    else -> Unit
                }
            }
            else -> controlOrPrint(b)
        }
    }

    private fun clearParser() {
        inEscape = false
        inCsi = false
        inOsc = false
        charsetSlot = null
        csiPrivate = false
        csiParams.clear()
        csiOverflow = false
        utf8.reset()
    }

    private fun reset(full: Boolean) {
        clearParser()
        lastWritten = null
        fg = DEFAULT_FG
        bg = DEFAULT_BG
        bold = false
        inverse = false
        wraparound = true
        originMode = false
        applicationCursor = false
        cursorVisible = true
        scrollTop = 0
        scrollBottom = rows - 1
        cursorCol = cursorCol.coerceAtMost(cols - 1)
        charsets.reset()
        savedCursor = SavedCursor(0, 0, fg, bg, bold, inverse, originMode, wraparound, charsets.snapshot())
        if (full) {
            bracketedPaste = false
            savedGrid = null
            alternateState = null
            grid = blankGrid(cols, rows)
            history.clear()
            homeCursor()
        }
    }

    private fun controlOrPrint(b: Int) {
        if (b < 0x20 || b == 0x7F) utf8.finish()
        when (b) {
            0x07 -> Unit // bell
            0x08 -> cursorCol = (cursorCol.coerceAtMost(cols - 1) - 1).coerceAtLeast(0)
            0x09 -> cursorCol = ((cursorCol / 8 + 1) * 8).coerceAtMost(cols - 1)
            0x0A, 0x0B, 0x0C -> lineFeed()
            0x0D -> cursorCol = 0
            0x0E -> charsets.use(1)
            0x0F -> charsets.use(0)
            in 0x00..0x1F, 0x7F -> Unit
            else -> utf8.feed(b)
        }
    }

    private fun putCodePoint(code: Int) {
        val type = Character.getType(code)
        if (type == Character.NON_SPACING_MARK.toInt() || type == Character.ENCLOSING_MARK.toInt()) {
            appendMark(code)
            return
        }
        if (cursorCol >= cols) {
            if (wraparound) {
                cursorCol = 0
                lineFeed()
            } else cursorCol = cols - 1
        }
        val text = String(Character.toChars(charsets.translate(code)))
        grid[cursorRow][cursorCol] = cell(text)
        lastWritten = Point(cursorRow, cursorCol)
        cursorCol = if (wraparound) cursorCol + 1 else (cursorCol + 1).coerceAtMost(cols - 1)
    }

    private fun appendMark(code: Int) {
        val col = if (!wraparound && cursorCol == cols - 1 &&
            lastWritten == Point(cursorRow, cursorCol)) cursorCol
            else (cursorCol - 1).coerceIn(0, cols - 1)
        val previous = grid[cursorRow][col]
        val mark = String(Character.toChars(code))
        if (previous.text.length + mark.length <= MAX_CELL_CODE_UNITS) {
            grid[cursorRow][col] = previous.copy(text = previous.text + mark)
        }
    }

    private fun cell(text: String) =
        if (inverse) Cell(text, bg, fg, bold) else Cell(text, fg, bg, bold)

    private fun blank() = Cell(" ", DEFAULT_FG, DEFAULT_BG, false)

    private fun lineFeed() {
        cursorCol = cursorCol.coerceAtMost(cols - 1)
        when {
            cursorRow == scrollBottom -> scrollUp(1)
            cursorRow < rows - 1 -> cursorRow++
        }
    }

    private fun reverseIndex() {
        cursorCol = cursorCol.coerceAtMost(cols - 1)
        when {
            cursorRow == scrollTop -> scrollDown(1)
            cursorRow > 0 -> cursorRow--
        }
    }

    private fun scrollUp(count: Int) {
        repeat(count.coerceIn(1, scrollBottom - scrollTop + 1)) {
            if (scrollTop == 0 && scrollBottom == rows - 1 && savedGrid == null) {
                appendHistory(grid[0])
            }
            for (r in scrollTop until scrollBottom) grid[r] = grid[r + 1]
            grid[scrollBottom] = blankRow()
        }
    }

    private fun appendHistory(row: Array<Cell>) {
        if (history.size == MAX_SCROLLBACK_ROWS) history.removeFirst()
        history.addLast(row)
        totalScrolled++
    }

    private fun scrollDown(count: Int) {
        repeat(count.coerceIn(1, scrollBottom - scrollTop + 1)) {
            for (r in scrollBottom downTo scrollTop + 1) grid[r] = grid[r - 1]
            grid[scrollTop] = blankRow()
        }
    }

    private fun handleCsi(final: Char) {
        if (final == 'p' && !csiPrivate && csiParams.toString() == "!") {
            reset(full = false)
            return
        }
        val parts = csiParams.toString().split(';').map { it.toIntOrNull() ?: 0 }
        val first = parts.getOrNull(0) ?: 0
        val count = first.coerceIn(1, maxOf(cols, rows))

        if (final == 'n' && parts.size == 1) {
            when {
                first == 5 && !csiPrivate -> onReply("\u001B[0n")
                first == 6 -> {
                    val prefix = if (csiPrivate) "?" else ""
                    val row = cursorRow - (if (originMode) scrollTop else 0) + 1
                    onReply("\u001B[$prefix$row;${cursorCol.coerceAtMost(cols - 1) + 1}R")
                }
            }
            return
        }

        if (csiPrivate) {
            handlePrivateMode(parts, final)
            return
        }

        when (final) {
            't' -> if (first == 18 && parts.size == 1) onReply("\u001B[8;$rows;${cols}t")
            'm' -> applySgr(parts)
            'H', 'f' -> {
                cursorRow = absoluteRow(parts.getOrNull(0) ?: 1)
                cursorCol = (parts.getOrNull(1) ?: 1).coerceAtLeast(1).coerceAtMost(cols) - 1
            }
            'A' -> {
                cursorCol = cursorCol.coerceAtMost(cols - 1)
                cursorRow = (cursorRow - count).coerceAtLeast(firstRow())
            }
            'B' -> {
                cursorCol = cursorCol.coerceAtMost(cols - 1)
                cursorRow = (cursorRow + count).coerceAtMost(lastRow())
            }
            'C' -> cursorCol = (cursorCol + count).coerceAtMost(cols - 1)
            'D' -> cursorCol = (cursorCol.coerceAtMost(cols - 1) - count).coerceAtLeast(0)
            'E' -> {
                cursorRow = (cursorRow + count).coerceAtMost(lastRow())
                cursorCol = 0
            }
            'F' -> {
                cursorRow = (cursorRow - count).coerceAtLeast(firstRow())
                cursorCol = 0
            }
            'G', '`' -> cursorCol = first.coerceAtLeast(1).coerceAtMost(cols) - 1
            'd' -> {
                cursorCol = cursorCol.coerceAtMost(cols - 1)
                cursorRow = absoluteRow(first)
            }
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
                if (top < bottom && top in 0 until rows && bottom in 0 until rows) {
                    scrollTop = top
                    scrollBottom = bottom
                    homeCursor()
                }
            }
            's' -> saveCursor()
            'u' -> restoreCursor()
            else -> Unit
        }
    }

    private fun handlePrivateMode(parts: List<Int>, final: Char) {
        if (final != 'h' && final != 'l') return
        val enable = final == 'h'
        for (mode in parts) {
            when (mode) {
                1 -> applicationCursor = enable
                6 -> {
                    originMode = enable
                    homeCursor()
                }
                7 -> {
                    wraparound = enable
                    cursorCol = cursorCol.coerceAtMost(cols - 1)
                }
                25 -> cursorVisible = enable
                2004 -> bracketedPaste = enable
                // Full screen programs swap to their own screen and back.
                47, 1047, 1049 -> if (enable) enterAlternateScreen() else leaveAlternateScreen()
            }
        }
    }

    private fun enterAlternateScreen() {
        if (savedGrid != null) return
        alternateState = AlternateState(cursorRow, cursorCol, scrollTop, scrollBottom,
            savedCursor, originMode, charsets.snapshot(), lastWritten)
        savedGrid = grid
        grid = blankGrid(cols, rows)
        cursorRow = 0
        cursorCol = 0
        scrollTop = 0
        scrollBottom = rows - 1
        savedCursor = null
        lastWritten = null
    }

    private fun leaveAlternateScreen() {
        val restored = savedGrid ?: return
        val state = alternateState ?: return
        grid = restored
        savedGrid = null
        alternateState = null
        cursorRow = state.row.coerceIn(0, rows - 1)
        cursorCol = state.col.coerceIn(0, cols - 1)
        scrollTop = state.top.coerceIn(0, rows - 1)
        scrollBottom = state.bottom.coerceIn(scrollTop, rows - 1)
        originMode = state.origin
        if (originMode) cursorRow = cursorRow.coerceIn(scrollTop, scrollBottom)
        savedCursor = state.savedCursor
        charsets.restore(state.charsets)
        lastWritten = state.lastWritten
    }

    private fun saveCursor() {
        savedCursor = SavedCursor(cursorRow, cursorCol, fg, bg, bold, inverse,
            originMode, wraparound, charsets.snapshot())
    }

    private fun restoreCursor() {
        val saved = savedCursor ?: return
        originMode = saved.origin
        wraparound = saved.wrap
        cursorRow = saved.row.coerceIn(firstRow(), lastRow())
        cursorCol = saved.col.coerceIn(0, cols - 1)
        fg = saved.fg
        bg = saved.bg
        bold = saved.bold
        inverse = saved.inverse
        charsets.restore(saved.charsets)
    }

    private fun firstRow() = if (originMode) scrollTop else 0

    private fun lastRow() = if (originMode) scrollBottom else rows - 1

    private fun absoluteRow(row: Int) =
        firstRow() + row.coerceIn(1, lastRow() - firstRow() + 1) - 1

    private fun homeCursor() {
        cursorRow = firstRow()
        cursorCol = 0
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
                            val colour = color256((list.getOrNull(i + 2) ?: 0).coerceIn(0, 255))
                            if (target == 38) fg = colour else bg = colour
                            i += 2
                        }
                        2 -> {
                            val r = (list.getOrNull(i + 2) ?: 0).coerceIn(0, 255)
                            val g = (list.getOrNull(i + 3) ?: 0).coerceIn(0, 255)
                            val b = (list.getOrNull(i + 4) ?: 0).coerceIn(0, 255)
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
            2 -> {
                for (r in 0 until rows) grid[r] = blankRow()
            }
            3 -> history.clear()
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
        private const val MAX_CELL_CODE_UNITS = 128
        const val MAX_SCROLLBACK_ROWS = 2000
        const val DEFAULT_FG = 1
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
            Array(rows) { Array(cols) { Cell(" ", DEFAULT_FG, DEFAULT_BG, false) } }
    }
}
