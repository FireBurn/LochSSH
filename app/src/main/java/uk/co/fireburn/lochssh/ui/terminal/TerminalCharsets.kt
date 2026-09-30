package uk.co.fireburn.lochssh.ui.terminal

internal class TerminalCharsets {
    enum class Set {
        ASCII, UK, GRAPHICS;

        fun translate(character: Char): Char = when {
            this == UK && character == '#' -> '£'
            this == GRAPHICS && character in '_'..'~' -> GRAPHICS_CHARACTERS[character - '_']
            else -> character
        }
    }

    data class State(val sets: List<Set>, val active: Int, val single: Int?)

    private var sets = List(4) { Set.ASCII }
    private var active = 0
    private var single: Int? = null

    fun designate(slot: Int, code: Char) {
        val selected = when (code) {
            '0', '2' -> Set.GRAPHICS
            'A' -> Set.UK
            'B', '1' -> Set.ASCII
            else -> return
        }
        sets = sets.mapIndexed { index, previous -> if (index == slot) selected else previous }
    }

    fun use(slot: Int) { active = slot }

    fun next(slot: Int) { single = slot }

    fun translate(character: Char): Char {
        val selected = sets[single ?: active]
        single = null
        return selected.translate(character)
    }

    fun snapshot() = State(sets, active, single)

    fun restore(state: State) {
        sets = state.sets
        active = state.active
        single = state.single
    }

    fun reset() {
        sets = List(4) { Set.ASCII }
        active = 0
        single = null
    }

    companion object {
        private const val GRAPHICS_CHARACTERS = " ◆▒␉␌␍␊°±␤␋┘┐┌└┼⎺⎻─⎼⎽├┤┴┬│≤≥π≠£·"
    }
}
