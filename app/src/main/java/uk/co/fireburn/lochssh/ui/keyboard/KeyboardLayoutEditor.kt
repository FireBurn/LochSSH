package uk.co.fireburn.lochssh.ui.keyboard

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uk.co.fireburn.lochssh.data.KeyboardAction
import uk.co.fireburn.lochssh.data.KeyboardButton
import uk.co.fireburn.lochssh.data.KeyboardLayout

@Composable
fun KeyboardLayoutEditor(layout: KeyboardLayout, onSave: (KeyboardLayout) -> Unit, onDismiss: () -> Unit) {
    var rows by remember(layout) { mutableStateOf(layout.rows) }
    var selected by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    fun replaceRow(index: Int, row: List<KeyboardButton>) {
        rows = rows.mapIndexed { i, old -> if (i == index) row else old }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Terminal keyboard") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Tap a key to change, move or remove it. Each row holds up to 10 keys.")
                rows.forEachIndexed { rowIndex, row ->
                    Column {
                        Row {
                            Text("Row ${rowIndex + 1}", Modifier.weight(1f))
                            TextButton(enabled = rowIndex > 0, onClick = {
                                rows = rows.toMutableList().apply {
                                    add(rowIndex - 1, removeAt(rowIndex))
                                }.toList()
                            }) { Text("Up") }
                            TextButton(enabled = rowIndex < rows.lastIndex, onClick = {
                                rows = rows.toMutableList().apply {
                                    add(rowIndex + 1, removeAt(rowIndex))
                                }.toList()
                            }) { Text("Down") }
                        }
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                            row.forEachIndexed { keyIndex, button ->
                                TextButton(onClick = { selected = rowIndex to keyIndex }) { Text(button.label) }
                            }
                            TextButton(enabled = row.size < KeyboardLayout.MAX_KEYS, onClick = {
                                selected = rowIndex to row.size
                            }) { Text("Add key") }
                        }
                        TextButton(enabled = rows.size > 1, onClick = {
                            rows = rows.filterIndexed { i, _ -> i != rowIndex }
                        }) { Text("Remove row") }
                    }
                }
                TextButton(enabled = rows.size < KeyboardLayout.MAX_ROWS, onClick = {
                    rows = rows + listOf(emptyList())
                }) { Text("Add row") }
            }
        },
        confirmButton = {
            TextButton(enabled = rows.all { it.isNotEmpty() }, onClick = {
                onSave(KeyboardLayout(rows.map { it.toList() }))
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { rows = KeyboardLayout.DEFAULT.rows }) { Text("Reset") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )

    selected?.let { (rowIndex, keyIndex) ->
        val row = rows[rowIndex]
        val existing = row.getOrNull(keyIndex)
        KeyboardButtonPicker(
            existing = existing,
            canMoveLeft = keyIndex in 1..row.lastIndex,
            canMoveRight = keyIndex < row.lastIndex,
            onMove = { step ->
                replaceRow(rowIndex, row.toMutableList().apply {
                    add(keyIndex + step, removeAt(keyIndex))
                }.toList())
                selected = null
            },
            onRemove = {
                replaceRow(rowIndex, row.filterIndexed { i, _ -> i != keyIndex })
                selected = null
            },
            onChoose = { button ->
                replaceRow(rowIndex, if (existing == null) row + button else row.mapIndexed { i, old ->
                    if (i == keyIndex) button else old
                })
                selected = null
            },
            onDismiss = { selected = null }
        )
    }
}

@Composable
private fun KeyboardButtonPicker(
    existing: KeyboardButton?,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    onChoose: (KeyboardButton) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(existing?.takeIf { it.action == KeyboardAction.TEXT }?.text.orEmpty()) }
    val validText = text.isNotEmpty() && text.length <= KeyboardButton.MAX_TEXT_LENGTH &&
        text.none { it.code < 32 || it.code == 127 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add key" else "Change ${existing.label}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (existing != null) {
                    Row {
                        TextButton(enabled = canMoveLeft, onClick = { onMove(-1) }) { Text("Left") }
                        TextButton(enabled = canMoveRight, onClick = { onMove(1) }) { Text("Right") }
                        TextButton(onClick = onRemove) { Text("Remove") }
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Text key") },
                    singleLine = true,
                    supportingText = { Text("Up to 16 characters; no line breaks") }
                )
                TextButton(enabled = validText, onClick = {
                    onChoose(KeyboardButton(KeyboardAction.TEXT, text))
                }) { Text("Use text") }
                KeyboardAction.entries.filter { it != KeyboardAction.TEXT }.forEach { action ->
                    TextButton(onClick = { onChoose(KeyboardButton(action)) }) { Text(action.label) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
