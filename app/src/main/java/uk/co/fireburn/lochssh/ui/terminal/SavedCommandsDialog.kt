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
    onSave: (Long, String, String) -> Unit,
    onDelete: (SavedCommandEntity) -> Unit,
    onInsert: (String) -> Unit,
    onRun: (String) -> Unit,
    onClose: () -> Unit
) {
    var editing by remember { mutableStateOf<SavedCommandEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<SavedCommandEntity?>(null) }

    fun edit(saved: SavedCommandEntity?) {
        editing = saved
        adding = true
        name = saved?.name.orEmpty()
        command = saved?.command.orEmpty()
    }

    val valid = name.isNotBlank() && command.isNotBlank() &&
        !command.contains('\n') && !command.contains('\r')

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (adding) if (editing == null) "New command" else "Edit command" else "Saved commands") },
        text = {
            if (adding) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        onSave(editing?.id ?: 0L, name, command)
                        adding = false
                        editing = null
                    },
                    enabled = valid
                ) { Text("Save") }
            } else {
                TextButton(onClick = { edit(null) }) { Text("Add") }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (adding) { adding = false; editing = null } else onClose()
            }) { Text(if (adding) "Cancel" else "Close") }
        }
    )

    deleting?.let { saved ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${saved.name}?") },
            confirmButton = {
                TextButton(onClick = { onDelete(saved); deleting = null }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Cancel") }
            }
        )
    }
}
