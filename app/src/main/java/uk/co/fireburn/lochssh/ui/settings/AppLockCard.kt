package uk.co.fireburn.lochssh.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.LifecycleResumeEffect
import uk.co.fireburn.lochssh.security.DeviceUnlock

@Composable
fun AppLockCard(enabled: Boolean, onChange: (Boolean) -> Unit,
    checkAvailable: (Context) -> Boolean = DeviceUnlock::available) {
    val context = LocalContext.current
    val currentCheck by rememberUpdatedState(checkAvailable)
    var available by remember(context) { mutableStateOf(currentCheck(context)) }
    LifecycleResumeEffect(context) {
        available = currentCheck(context)
        onPauseOrDispose { }
    }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("App lock", style = MaterialTheme.typography.titleMedium)
                Text("Require device unlock after leaving the app. Blocks screenshots; connections stay running.")
                if (!available) Text("Set a PIN, pattern or password in Android settings first.")
            }
            Switch(checked = enabled, enabled = enabled || available, onCheckedChange = onChange,
                modifier = Modifier.semantics { contentDescription = "App lock" })
        }
    }
}
