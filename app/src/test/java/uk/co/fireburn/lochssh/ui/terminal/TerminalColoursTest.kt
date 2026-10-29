package uk.co.fireburn.lochssh.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalColoursTest {
    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    @Test
    fun contrastRatioMatchesTheKnownExtremesAndIsSymmetric() {
        assertEquals(21.0, ColourContrast.ratio(black, white), 0.01)
        assertEquals(1.0, ColourContrast.ratio(white, white), 0.0001)
        assertEquals(ColourContrast.ratio(0xFF336699.toInt(), white), ColourContrast.ratio(white, 0xFF336699.toInt()), 0.0001)
    }

    @Test
    fun unreadableTextIsMovedJustFarEnoughAndReadableTextIsLeftAlone() {
        val navy = 0xFF0000EE.toInt()
        val dark = 0xFF0D1B2A.toInt()
        assertTrue(ColourContrast.ratio(navy, dark) < 4.5)
        val fixed = ColourContrast.ensure(navy, dark, 4.5f)
        assertTrue(ColourContrast.ratio(fixed, dark) >= 4.5)
        assertTrue("stays close to its own colour", ColourContrast.ratio(fixed, dark) < 5.2)
        val fine = 0xFFE0E1DD.toInt()
        assertEquals(fine, ColourContrast.ensure(fine, dark, 4.5f))
    }

    @Test
    fun darkTextOnADarkBackgroundGoesLightAndLightOnLightGoesDark() {
        assertTrue(ColourContrast.luminance(ColourContrast.ensure(0xFF222222.toInt(), black, 4.5f)) > 0.1)
        assertTrue(ColourContrast.luminance(ColourContrast.ensure(0xFFDDDDDD.toInt(), white, 4.5f)) < 0.5)
    }

    @Test
    fun aMiddleGreyBackgroundStillGetsTheBestAvailableTextColour() {
        val grey = 0xFF777777.toInt()
        val fixed = ColourContrast.ensure(grey, grey, 7f)
        assertTrue(ColourContrast.ratio(fixed, grey) > 4.0)
    }

    @Test
    fun textTheSameColourAsItsBackgroundStaysHidden() {
        val colours = TerminalColours(TerminalTheme.LOCH, TerminalPalette.STANDARD, MinimumContrast.STRONG)
        val red = TerminalBuffer.ANSI_COLORS[1]
        assertEquals(red, colours.foreground(red, red))
    }

    @Test
    fun theColourBlindPaletteReplacesTheSixteenAndLeavesOtherColoursAlone() {
        val p = TerminalPalette.COLOUR_BLIND
        assertEquals(0xFFD55E00.toInt(), p.map(TerminalBuffer.ANSI_COLORS[1]))
        assertEquals(0xFF56B4E9.toInt(), p.map(TerminalBuffer.ANSI_COLORS[2]))
        assertEquals(0xFF123456.toInt(), p.map(0xFF123456.toInt()))
        assertEquals(TerminalBuffer.ANSI_COLORS[1], TerminalPalette.STANDARD.map(TerminalBuffer.ANSI_COLORS[1]))
    }

    // Colour-blind vision simulated with Machado, Oliveira and Fernandes' full-severity matrices on linear RGB.
    private fun simulate(argb: Int, m: Array<DoubleArray>): DoubleArray {
        fun lin(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        val rgb = doubleArrayOf(lin(16), lin(8), lin(0))
        return DoubleArray(3) { r -> (m[r][0] * rgb[0] + m[r][1] * rgb[1] + m[r][2] * rgb[2]).coerceIn(0.0, 1.0) }
    }

    private fun lab(rgb: DoubleArray): DoubleArray {
        val x = (0.4124 * rgb[0] + 0.3576 * rgb[1] + 0.1805 * rgb[2]) / 0.95047
        val y = 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2]
        val z = (0.0193 * rgb[0] + 0.1192 * rgb[1] + 0.9505 * rgb[2]) / 1.08883
        fun f(t: Double) = if (t > 0.008856) Math.cbrt(t) else 7.787 * t + 16.0 / 116
        return doubleArrayOf(116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z)))
    }

    private fun apart(a: Int, b: Int, m: Array<DoubleArray>): Double {
        val la = lab(simulate(a, m))
        val lb = lab(simulate(b, m))
        return Math.sqrt((0..2).sumOf { (la[it] - lb[it]) * (la[it] - lb[it]) })
    }

    private val deuteranopia = arrayOf(
        doubleArrayOf(0.367322, 0.860646, -0.227968),
        doubleArrayOf(0.280085, 0.672501, 0.047413),
        doubleArrayOf(-0.011820, 0.042940, 0.968881))
    private val protanopia = arrayOf(
        doubleArrayOf(0.152286, 1.052583, -0.204868),
        doubleArrayOf(0.114503, 0.786281, 0.099216),
        doubleArrayOf(-0.003882, -0.048116, 1.051998))

    @Test
    fun redAndGreenAreFurtherApartForRedGreenColourBlindnessInTheSafePalette() {
        val p = TerminalPalette.COLOUR_BLIND
        for ((name, matrix) in listOf("deuteranopia" to deuteranopia, "protanopia" to protanopia)) {
            val before = apart(TerminalBuffer.ANSI_COLORS[1], TerminalBuffer.ANSI_COLORS[2], matrix)
            val after = apart(p.map(TerminalBuffer.ANSI_COLORS[1]), p.map(TerminalBuffer.ANSI_COLORS[2]), matrix)
            assertTrue("$name: red and green ${"%.1f".format(before)} apart before, ${"%.1f".format(after)} after", after > before * 1.3)
            val brightBefore = apart(TerminalBuffer.BRIGHT_COLORS[1], TerminalBuffer.BRIGHT_COLORS[2], matrix)
            val brightAfter = apart(p.map(TerminalBuffer.BRIGHT_COLORS[1]), p.map(TerminalBuffer.BRIGHT_COLORS[2]), matrix)
            assertTrue("$name bright: $brightBefore to $brightAfter", brightAfter > brightBefore)
        }
    }

    @Test
    fun everyPaletteEntryIsReadableOnTheDefaultThemesWhenContrastIsOn() {
        for (palette in TerminalPalette.entries) for (theme in TerminalTheme.entries) {
            val colours = TerminalColours(theme, palette, MinimumContrast.READABLE)
            for (index in 1..15) {
                val named = if (index < 8) TerminalBuffer.ANSI_COLORS[index] else TerminalBuffer.BRIGHT_COLORS[index - 8]
                val shown = colours.foreground(named, TerminalBuffer.DEFAULT_BG)
                assertTrue("$palette on $theme index $index", ColourContrast.ratio(shown, colours.background(TerminalBuffer.DEFAULT_BG)) >= 4.49)
            }
        }
    }

    @Test
    fun settingsValuesFallBackToTheDefaults() {
        assertEquals(TerminalPalette.STANDARD, TerminalPalette.decode(null))
        assertEquals(TerminalPalette.COLOUR_BLIND, TerminalPalette.decode("COLOUR_BLIND"))
        assertEquals(MinimumContrast.READABLE, MinimumContrast.decode("nonsense"))
    }
}
