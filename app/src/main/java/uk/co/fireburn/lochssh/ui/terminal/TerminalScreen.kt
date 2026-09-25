package uk.co.fireburn.lochssh.ui.terminal

import android.content.Context
import android.content.Intent
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import uk.co.fireburn.lochssh.service.SshForegroundService
import uk.co.fireburn.lochssh.ui.keyboard.ImeKeyEncoder
import uk.co.fireburn.lochssh.ui.keyboard.JuiceSshKeyboardBar
import uk.co.fireburn.lochssh.ui.keyboard.TerminalEditText
import uk.co.fireburn.lochssh.ui.keyboard.TerminalImeInput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    sessionId: Long,
    onBack: () -> Unit,
    viewModel: TerminalViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val fontSize by viewModel.fontSize.collectAsStateWithLifecycle()
    val imm = remember {
        context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }
    val modifiers = remember { TerminalModifiers() }
    var imeVisible by remember { mutableStateOf(false) }
    var imeEditText by remember { mutableStateOf<TerminalEditText?>(null) }
    var hadSession by remember(sessionId) { mutableStateOf(false) }

    val session by viewModel.session.collectAsStateWithLifecycle()
    val manager = session?.manager

    LaunchedEffect(sessionId) { viewModel.load(sessionId) }
    LaunchedEffect(sessionId, session?.id) {
        if (session != null) hadSession = true
        else if (hadSession) onBack()
    }

    Scaffold(
        // Only the top inset: the content pads itself against whichever of the
        // keyboard or the navigation bar is taller, so the bar sits on the keyboard.
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        session?.label?.ifBlank { null } ?: "Terminal",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        SshForegroundService.disconnect(context, sessionId)
                        onBack()
                    }) { Text("Disconnect") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
        ) {
            val error = session?.error
            if (error != null && manager == null) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        error,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = {
                        session?.let { SshForegroundService.start(context, it.hostId, it.id) }
                    }) { Text("Retry") }
                }
            } else {
                TerminalView(
                    manager = manager,
                    fontSize = fontSize.sp,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
            TerminalImeInput(
                onText = {
                    manager?.write(
                        ImeKeyEncoder.encodeText(it, modifiers.ctrl, modifiers.alt)
                    )
                    modifiers.clear()
                },
                onKey = { key ->
                    val bytes = ImeKeyEncoder.encode(key, modifiers.ctrl, modifiers.alt)
                    if (bytes != null) {
                        manager?.write(bytes)
                        modifiers.clear()
                        true
                    } else {
                        false
                    }
                },
                onDelete = { n -> manager?.write(ByteArray(n) { 0x7F }) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .graphicsLayer(alpha = 0f),
                onReady = { imeEditText = it }
            )
            JuiceSshKeyboardBar(
                modifiers = modifiers,
                onSend = { manager?.write(it) },
                onFontSizeChange = { viewModel.changeFontSize(it) },
                onImeToggle = {
                    val view = imeEditText ?: return@JuiceSshKeyboardBar
                    if (imeVisible) {
                        imm.hideSoftInputFromWindow(view.windowToken, 0)
                        view.clearFocus()
                    } else {
                        view.requestFocus()
                        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                    }
                    imeVisible = !imeVisible
                }
            )
        }
    }
}
