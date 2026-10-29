package uk.co.fireburn.lochssh.ui.terminal

import kotlin.math.pow
import kotlin.math.roundToInt

// The sixteen named colours a program asks for, and how they are shown.
enum class TerminalPalette(val label: String, val description: String, private val colours: IntArray?) {
    STANDARD("Standard", "The usual terminal colours.", null),
    // Okabe and Ito's set, chosen to stay apart for the common kinds of colour blindness: red and green become
    // orange and blue, the pair that diffs and test results rely on.
    COLOUR_BLIND("Colour-blind safe", "Orange and blue in place of red and green; every colour stays distinct for most kinds of colour blindness.", intArrayOf(
        0xFF000000.toInt(), 0xFFD55E00.toInt(), 0xFF56B4E9.toInt(), 0xFFF0E442.toInt(),
        0xFF0072B2.toInt(), 0xFFCC79A7.toInt(), 0xFF009E73.toInt(), 0xFFE0E0E0.toInt(),
        0xFF808080.toInt(), 0xFFFF8F40.toInt(), 0xFF9AD4F5.toInt(), 0xFFFFF59D.toInt(),
        0xFF4D9FDD.toInt(), 0xFFE8A9C8.toInt(), 0xFF33C9A0.toInt(), 0xFFFFFFFF.toInt()
    )),
    HIGH_CONTRAST("High contrast", "Pure, bright colours with no dark blues or dim greys.", intArrayOf(
        0xFF000000.toInt(), 0xFFFF4040.toInt(), 0xFF40FF40.toInt(), 0xFFFFFF40.toInt(),
        0xFF6CA0FF.toInt(), 0xFFFF70FF.toInt(), 0xFF40FFFF.toInt(), 0xFFFFFFFF.toInt(),
        0xFFB0B0B0.toInt(), 0xFFFF7070.toInt(), 0xFF70FF70.toInt(), 0xFFFFFF70.toInt(),
        0xFF9CBCFF.toInt(), 0xFFFF9CFF.toInt(), 0xFF70FFFF.toInt(), 0xFFFFFFFF.toInt()
    ));

    // The colour to draw in place of one of the standard sixteen; anything else is returned as it is.
    fun map(argb: Int): Int {
        val target = colours ?: return argb
        val index = standard.indexOf(argb)
        return if (index >= 0) target[index] else argb
    }

    companion object {
        private val standard = TerminalBuffer.ANSI_COLORS + TerminalBuffer.BRIGHT_COLORS
        fun decode(value: String?): TerminalPalette = entries.firstOrNull { it.name == value } ?: STANDARD
    }
}

// How readable text must be against what it is drawn on. The number is the contrast ratio WCAG uses.
enum class MinimumContrast(val label: String, val ratio: Float) {
    OFF("Off", 0f),
    LARGE("3:1", 3f),
    READABLE("4.5:1", 4.5f),
    STRONG("7:1", 7f);

    companion object {
        fun decode(value: String?): MinimumContrast = entries.firstOrNull { it.name == value } ?: READABLE
    }
}

class TerminalColours(val theme: TerminalTheme, val palette: TerminalPalette, val minimum: MinimumContrast) {
    fun background(bg: Int): Int = palette.map(theme.color(bg))

    // The text colour to use for fg drawn over bg, moved toward white or black only as far as the contrast needs.
    fun foreground(fg: Int, bg: Int): Int {
        val text = palette.map(theme.color(fg))
        val behind = background(bg)
        // Text the program made the same colour as its background is hidden on purpose.
        if (text == behind || minimum == MinimumContrast.OFF) return text
        return ColourContrast.ensure(text, behind, minimum.ratio)
    }
}

object ColourContrast {
    fun luminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    fun ratio(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    // Blends the colour toward white or black until the ratio is met. The direction that can get further
    // from the background is tried first, and the closest it can get is used if neither reaches it.
    fun ensure(colour: Int, background: Int, minimum: Float): Int {
        if (ratio(colour, background) >= minimum) return colour
        val lighterFirst = luminance(background) < 0.18
        val order = if (lighterFirst) listOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt()) else listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        for (extreme in order) {
            if (ratio(extreme, background) < minimum) continue
            var low = 0.0
            var high = 1.0
            repeat(16) {
                val mid = (low + high) / 2
                if (ratio(blend(colour, extreme, mid), background) >= minimum) high = mid else low = mid
            }
            return blend(colour, extreme, high)
        }
        return order.maxByOrNull { ratio(it, background) } ?: colour
    }

    private fun blend(from: Int, to: Int, amount: Double): Int {
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * amount).roundToInt().coerceIn(0, 255)
        }
        return 0xFF000000.toInt() or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
