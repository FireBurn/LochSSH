package uk.co.fireburn.lochssh.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _terminalFontSize = MutableStateFlow(
        prefs.getInt(KEY_FONT_SIZE, DEFAULT_FONT_SIZE).coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
    )
    val terminalFontSize: StateFlow<Int> = _terminalFontSize

    fun setTerminalFontSize(size: Int) {
        val clamped = size.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        if (clamped == _terminalFontSize.value) return
        prefs.edit().putInt(KEY_FONT_SIZE, clamped).apply()
        _terminalFontSize.value = clamped
    }

    fun changeTerminalFontSize(step: Int) = setTerminalFontSize(_terminalFontSize.value + step)

    companion object {
        const val MIN_FONT_SIZE = 8
        const val MAX_FONT_SIZE = 28
        const val DEFAULT_FONT_SIZE = 14

        private const val FILE_NAME = "lochssh_settings"
        private const val KEY_FONT_SIZE = "terminal_font_size"
    }
}
