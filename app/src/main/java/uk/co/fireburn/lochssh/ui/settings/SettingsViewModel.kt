package uk.co.fireburn.lochssh.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.fireburn.lochssh.data.AppPreferences
import uk.co.fireburn.lochssh.data.KeyboardLayout
import uk.co.fireburn.lochssh.data.ConnectionHistoryRecorder
import uk.co.fireburn.lochssh.data.EncryptedStorageManager
import uk.co.fireburn.lochssh.data.db.LochSshDatabase
import uk.co.fireburn.lochssh.ssh.HostKeyTrust
import uk.co.fireburn.lochssh.ui.terminal.TerminalTheme
import uk.co.fireburn.lochssh.ui.terminal.TerminalTypeface
import uk.co.fireburn.lochssh.ui.hosts.HostShortcuts
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: AppPreferences,
    private val database: LochSshDatabase,
    private val secrets: EncryptedStorageManager,
    private val history: ConnectionHistoryRecorder
) : ViewModel() {

    val fontSize = preferences.terminalFontSize
    val sessionShortcuts = preferences.sessionShortcuts
    fun setSessionShortcuts(enabled: Boolean) = preferences.setSessionShortcuts(enabled)
    val terminalTypeface = preferences.terminalTypeface
    fun setTerminalTypeface(typeface: TerminalTypeface) = preferences.setTerminalTypeface(typeface)
    val terminalTheme = preferences.terminalTheme
    fun setTerminalTheme(theme: TerminalTheme) = preferences.setTerminalTheme(theme)
    val keyboardLayout = preferences.keyboardLayout
    fun setKeyboardLayout(layout: KeyboardLayout) = preferences.setKeyboardLayout(layout)
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun changeFontSize(step: Int) = preferences.changeTerminalFontSize(step)

    fun clearAll() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    secrets.change {
                        HostShortcuts.disable(context)
                        history.clear()
                        database.clearAllTables()
                        secrets.clear()
                        HostKeyTrust.clear(context)
                        preferences.reset()
                    }
                }
            }.onFailure { _error.value = "Could not clear all data: ${it.message ?: "storage error"}" }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
