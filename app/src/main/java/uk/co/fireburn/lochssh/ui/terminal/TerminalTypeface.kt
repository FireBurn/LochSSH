package uk.co.fireburn.lochssh.ui.terminal

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import uk.co.fireburn.lochssh.R

enum class TerminalTypeface(val label: String, regular: Int, bold: Int) {
    NOTO("Noto Sans Mono", R.font.noto_sans_mono_regular, R.font.noto_sans_mono_bold),
    JETBRAINS("JetBrains Mono", R.font.jetbrains_mono_regular, R.font.jetbrains_mono_bold);

    val family = FontFamily(Font(regular, FontWeight.Normal), Font(bold, FontWeight.Bold))

    companion object {
        fun decode(value: String?): TerminalTypeface = entries.firstOrNull { it.name == value } ?: NOTO
    }
}
