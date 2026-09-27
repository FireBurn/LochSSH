package uk.co.fireburn.lochssh.ui.terminal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uk.co.fireburn.lochssh.data.db.SavedCommandEntity

@Composable
fun SavedCommandsDialog(
    commands: List<SavedCommandEntity>,
    onSave: (Long, String, String, (String?) -> Unit) -> Unit,
    onDelete: (SavedCommandEntity, (String?) -> Unit) -> Unit,
    onInsert: (String) -> Unit,
    onRun: (String) -> Unit,
    onClose: () -> Unit
) {
    var editing by remember { mutableStateOf<SavedCommandEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<SavedCommandEntity?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun edit(saved: SavedCommandEntity?) {
        editing = saved
        adding = true
        name = saved?.name.orEmpty()
        command = saved?.command.orEmpty()
        error = null
    }

    val valid = name.isNotBlank() && command.isNotBlank() &&
        !command.contains('\n') && !command.contains('\r')

    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(if (adding) if (editing == null) "New command" else "Edit command" else "Saved commands") },
        text = {
            if (adding) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it },
                        label = { Text("Command") },
                        singleLine = true,
                        isError = command.contains('\n') || command.contains('\r')
                    )
                    Text("One line only. Run sends Enter; Insert does not. Don't save passwords here.")
                }
            } else if (commands.isEmpty()) {
                Text("No saved commands")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(commands, key = { it.id }) { saved ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(saved.name)
                            Text(saved.command, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Row {
                                TextButton(onClick = { onInsert(saved.command); onClose() }) {
                                    Text("Insert")
                                }
                                TextButton(onClick = { onRun(saved.command); onClose() }) {
                                    Text("Run")
                                }
                            }
                            Row {
                                TextButton(onClick = { edit(saved) }) { Text("Edit") }
                                TextButton(onClick = { deleting = saved }) { Text("Delete") }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (adding) {
                TextButton(
                    onClick = {
                        busy = true
                        error = null
                        onSave(editing?.id ?: 0L, name, command) { failure ->
                            busy = false
                            error = failure
                            if (failure == null) { adding = false; editing = null }
                        }
                    },
                    enabled = valid && !busy
                ) { Text("Save") }
            } else {
                TextButton(onClick = { edit(null) }, enabled = !busy) { Text("Add") }
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = {
                if (adding) { adding = false; editing = null } else onClose()
            }) { Text(if (adding) "Cancel" else "Close") }
        }
    )

    if (!adding && error != null) {
        AlertDialog(onDismissRequest = { error = null }, title = { Text("Command not deleted") },
            text = { Text(error.orEmpty()) }, confirmButton = {
                TextButton(onClick = { error = null }) { Text("OK") }
            })
    }

    deleting?.let { saved ->
        AlertDialog(
            onDismissRequest = { if (!busy) deleting = null },
            title = { Text("Delete ${saved.name}?") },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    onDelete(saved) { failure ->
                        busy = false
                        error = failure
                        deleting = null
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { deleting = null }) { Text("Cancel") }
            }
        )
    }
}
