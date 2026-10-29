package uk.co.fireburn.lochssh.ui.terminal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Asks what to do with an address. When the terminal could not tell where a program broke a line, each
// possible address is listed so the right one can be picked.
@Composable
internal fun LinkChoice(links: List<String>, onOpen: (String) -> Unit, onCopy: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (links.size == 1) "Link" else "Which link?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (links.size > 1) {
                    Text("This could be a link that a program split across lines. Pick the one that looks complete.",
                        style = MaterialTheme.typography.bodySmall)
                }
                links.forEachIndexed { index, link ->
                    Column {
                        if (links.size > 1) {
                            Text(if (index == 0) "Joined across lines" else "Shorter",
                                style = MaterialTheme.typography.labelSmall)
                        }
                        Text(link, maxLines = 6, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { onCopy(link) }) { Text("Copy") }
                            TextButton(onClick = { onOpen(link) }) { Text("Open") }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
