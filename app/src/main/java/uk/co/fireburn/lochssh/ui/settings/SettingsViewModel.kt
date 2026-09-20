package uk.co.fireburn.lochssh.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import uk.co.fireburn.lochssh.data.db.LochSshDatabase
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    fun clearAll() {
        viewModelScope.launch {
            context.deleteDatabase(LochSshDatabase.NAME)
            context.deleteFile("lochssh_secrets.xml")
        }
    }
}
