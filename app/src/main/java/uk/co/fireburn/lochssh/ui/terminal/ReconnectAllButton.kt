package uk.co.fireburn.lochssh.ui.terminal

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
internal fun ReconnectAllButton(available: Boolean, onClick: () -> Unit) {
    if (available) {
        TextButton(onClick = onClick) { Text("Reconnect all dropped sessions") }
    }
}
