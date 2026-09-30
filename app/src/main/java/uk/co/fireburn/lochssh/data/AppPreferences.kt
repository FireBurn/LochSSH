package uk.co.fireburn.lochssh.data

import android.content.Context
import java.io.IOException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton
import uk.co.fireburn.lochssh.ui.terminal.TerminalTheme
import uk.co.fireburn.lochssh.ui.terminal.TerminalTypeface

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _terminalFontSize = MutableStateFlow(
        prefs.getInt(KEY_FONT_SIZE, DEFAULT_FONT_SIZE).coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
    )
    val terminalFontSize: StateFlow<Int> = _terminalFontSize
    private val _terminalTheme = MutableStateFlow(TerminalTheme.decode(prefs.getString(KEY_THEME, null)))
    val terminalTheme: StateFlow<TerminalTheme> = _terminalTheme
    private val _terminalTypeface = MutableStateFlow(TerminalTypeface.decode(prefs.getString(KEY_TYPEFACE, null)))
    val terminalTypeface: StateFlow<TerminalTypeface> = _terminalTypeface
    private val _sessionShortcuts = MutableStateFlow(prefs.getBoolean(KEY_SHORTCUTS, false))
    val sessionShortcuts: StateFlow<Boolean> = _sessionShortcuts

    fun setSessionShortcuts(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHORTCUTS, enabled).apply()
        _sessionShortcuts.value = enabled
    }

    fun setTerminalTypeface(typeface: TerminalTypeface) {
        prefs.edit().putString(KEY_TYPEFACE, typeface.name).apply()
        _terminalTypeface.value = typeface
    }

    fun setTerminalTheme(theme: TerminalTheme) {
        prefs.edit().putString(KEY_THEME, theme.name).apply()
        _terminalTheme.value = theme
    }

    private val _keyboardLayout = MutableStateFlow(KeyboardLayout.decode(prefs.getString(KEY_KEYBOARD, null)))
    val keyboardLayout: StateFlow<KeyboardLayout> = _keyboardLayout

    fun setKeyboardLayout(layout: KeyboardLayout) {
        prefs.edit().putString(KEY_KEYBOARD, layout.encode()).apply()
        _keyboardLayout.value = layout
    }

    fun setTerminalFontSize(size: Int) {
        val clamped = size.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        if (clamped == _terminalFontSize.value) return
        prefs.edit().putInt(KEY_FONT_SIZE, clamped).apply()
        _terminalFontSize.value = clamped
    }

    fun changeTerminalFontSize(step: Int) = setTerminalFontSize(_terminalFontSize.value + step)

    fun reset() {
        if (!prefs.edit().clear().commit()) throw IOException("Could not clear settings")
        _terminalFontSize.value = DEFAULT_FONT_SIZE
        _keyboardLayout.value = KeyboardLayout.DEFAULT
        _terminalTheme.value = TerminalTheme.LOCH
        _terminalTypeface.value = TerminalTypeface.NOTO
        _sessionShortcuts.value = false
    }

    companion object {
        const val MIN_FONT_SIZE = 8
        const val MAX_FONT_SIZE = 28
        const val DEFAULT_FONT_SIZE = 14

        private const val FILE_NAME = "lochssh_settings"
        private const val KEY_FONT_SIZE = "terminal_font_size"
        private const val KEY_KEYBOARD = "keyboard_layout"
        private const val KEY_THEME = "terminal_theme"
        private const val KEY_TYPEFACE = "terminal_typeface"
        private const val KEY_SHORTCUTS = "session_shortcuts"
    }
}
