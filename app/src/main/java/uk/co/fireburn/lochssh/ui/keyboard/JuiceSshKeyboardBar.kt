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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.co.fireburn.lochssh.R
import uk.co.fireburn.lochssh.ui.terminal.TerminalKey
import uk.co.fireburn.lochssh.ui.terminal.TerminalKeyEncoder

@Composable
fun JuiceSshKeyboardBar(
    onSend: (ByteArray) -> Unit,
    onImeToggle: () -> Unit,
    onFontSizeChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var ctrl by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }
    var fn by remember { mutableStateOf(false) }

    fun emit(key: TerminalKey) {
        if (key == TerminalKey.ImeToggle) {
            onImeToggle()
            return
        }
        TerminalKeyEncoder.encode(key, ctrl, alt, fn)?.let(onSend)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Key("ESC", Modifier.weight(1f)) { emit(TerminalKey.Esc) }
            Key("/", Modifier.weight(1f)) { emit(TerminalKey.Text("/")) }
            Key("|", Modifier.weight(1f)) { emit(TerminalKey.Text("|")) }
            Key("-", Modifier.weight(1f)) { emit(TerminalKey.Text("-")) }
            Key("▲", Modifier.weight(1f)) { emit(TerminalKey.Arrow('A')) }
            Key("HOME", Modifier.weight(1f)) { emit(TerminalKey.Home) }
            Key("END", Modifier.weight(1f)) { emit(TerminalKey.End) }
            Key("PGUP", Modifier.weight(1f)) { emit(TerminalKey.PgUp) }
            Key("FN", Modifier.weight(1f), active = fn) { fn = !fn }
            Key("Aa+", Modifier.weight(1f)) { onFontSizeChange(1) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Key("TAB", Modifier.weight(1f)) { emit(TerminalKey.Tab) }
            Key("CTRL", Modifier.weight(1f), active = ctrl) { ctrl = !ctrl }
            Key("ALT", Modifier.weight(1f), active = alt) { alt = !alt }
            Key("◄", Modifier.weight(1f)) { emit(TerminalKey.Arrow('D')) }
            Key("▼", Modifier.weight(1f)) { emit(TerminalKey.Arrow('B')) }
            Key("►", Modifier.weight(1f)) { emit(TerminalKey.Arrow('C')) }
            Key("DEL", Modifier.weight(1f)) { emit(TerminalKey.Del) }
            Key("PGDN", Modifier.weight(1f)) { emit(TerminalKey.PgDn) }
            IconKey(
                painter = painterResource(R.drawable.ic_keyboard),
                contentDescription = "Show or hide the keyboard",
                modifier = Modifier.weight(1f)
            ) { emit(TerminalKey.ImeToggle) }
            Key("Aa-", Modifier.weight(1f)) { onFontSizeChange(-1) }
        }
    }
}

@Composable
private fun Key(
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(40.dp),
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
