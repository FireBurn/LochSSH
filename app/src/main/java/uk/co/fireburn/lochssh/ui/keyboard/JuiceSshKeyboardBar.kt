package uk.co.fireburn.lochssh.ui.keyboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.co.fireburn.lochssh.R
import uk.co.fireburn.lochssh.data.KeyboardAction
import uk.co.fireburn.lochssh.data.KeyboardButton
import uk.co.fireburn.lochssh.data.KeyboardLayout
import uk.co.fireburn.lochssh.ui.terminal.TerminalKeyEncoder
import uk.co.fireburn.lochssh.ui.terminal.TerminalModifiers

@Composable
fun JuiceSshKeyboardBar(
    modifiers: TerminalModifiers,
    onSend: (ByteArray) -> Unit,
    onImeToggle: () -> Unit,
    onFontSizeChange: (Int) -> Unit,
    layout: KeyboardLayout = KeyboardLayout.DEFAULT,
    applicationCursor: () -> Boolean = { false },
    modifier: Modifier = Modifier,
    inputTarget: Any? = null
) {
    fun emit(button: KeyboardButton, clearModifiers: Boolean = true) {
        when (button.action) {
            KeyboardAction.CTRL -> modifiers.ctrl = !modifiers.ctrl
            KeyboardAction.ALT -> modifiers.alt = !modifiers.alt
            KeyboardAction.FN -> modifiers.fn = !modifiers.fn
            KeyboardAction.KEYBOARD -> onImeToggle()
            KeyboardAction.FONT_UP -> onFontSizeChange(1)
            KeyboardAction.FONT_DOWN -> onFontSizeChange(-1)
            else -> {
                button.terminalKey()?.let { key ->
                    TerminalKeyEncoder.encode(key, modifiers.ctrl, modifiers.alt, modifiers.fn,
                        applicationCursor())?.let(onSend)
                }
                if (clearModifiers) modifiers.clear()
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        layout.rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { button ->
                    if (button.action == KeyboardAction.KEYBOARD) {
                        IconKey(
                            painterResource(R.drawable.ic_keyboard), "Show or hide the keyboard",
                            Modifier.weight(1f)
                        ) { emit(button) }
                    } else {
                        val active = when (button.action) {
                            KeyboardAction.CTRL -> modifiers.ctrl
                            KeyboardAction.ALT -> modifiers.alt
                            KeyboardAction.FN -> modifiers.fn
                            else -> false
                        }
                        val repeats = button.action in setOf(KeyboardAction.UP, KeyboardAction.DOWN,
                            KeyboardAction.LEFT, KeyboardAction.RIGHT, KeyboardAction.PAGE_UP,
                            KeyboardAction.PAGE_DOWN)
                        Key(button.label, Modifier.weight(1f), active, repeats, modifiers::clear, inputTarget) {
                            emit(button, clearModifiers = !repeats)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Key(
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    repeats: Boolean = false,
    onRelease: () -> Unit = {},
    inputTarget: Any? = null,
    onClick: () -> Unit
) {
    val keyModifier = modifier.height(40.dp).repeatingKey(repeats, onRelease, onClick, inputTarget)
    val colour = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val contentColour = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    if (repeats) {
        Surface(modifier = keyModifier, shape = RoundedCornerShape(6.dp),
            color = colour, contentColor = contentColour) { KeyLabel(label) }
        return
    }
    Surface(
        onClick = onClick,
        modifier = keyModifier,
        shape = RoundedCornerShape(6.dp),
        color = if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (active) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                maxLines = 1,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun KeyLabel(label: String) {
    Box(contentAlignment = Alignment.Center) {
        Text(label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            maxLines = 1, fontSize = 11.sp)
    }
}

@Composable
private fun IconKey(
    painter: Painter,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painter, contentDescription, modifier = Modifier.size(20.dp))
        }
    }
}
