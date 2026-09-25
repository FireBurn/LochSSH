package uk.co.fireburn.lochssh.ui.terminal

import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import uk.co.fireburn.lochssh.data.db.RemoteSessionModes
import uk.co.fireburn.lochssh.ui.keyboard.ImeKeyEncoder
import uk.co.fireburn.lochssh.ui.keyboard.JuiceSshKeyboardBar
import uk.co.fireburn.lochssh.ui.keyboard.TerminalEditText
import uk.co.fireburn.lochssh.ui.keyboard.TerminalImeInput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    sessionId: Long,
    onBack: () -> Unit,
    onFiles: () -> Unit,
    viewModel: TerminalViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val fontSize by viewModel.fontSize.collectAsStateWithLifecycle()
    val imm = remember {
        context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }
    val clipboard = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    val modifiers = remember { TerminalModifiers() }
    var imeVisible by remember { mutableStateOf(false) }
    var imeEditText by remember { mutableStateOf<TerminalEditText?>(null) }
    var hadSession by remember(sessionId) { mutableStateOf(false) }
    var scrollbackOffset by remember(sessionId) { mutableIntStateOf(0) }
    var liveRequest by remember(sessionId) { mutableIntStateOf(0) }
    var selectionClearKey by remember(sessionId) { mutableIntStateOf(0) }
    var selectedText by remember(sessionId) { mutableStateOf<String?>(null) }
    var terminalBuffer by remember(sessionId) { mutableStateOf<TerminalBuffer?>(null) }
    var pendingPaste by remember(sessionId) { mutableStateOf<String?>(null) }
    var moreOpen by remember { mutableStateOf(false) }

    val session by viewModel.session.collectAsStateWithLifecycle()
    val manager = session?.manager

    fun sendPaste(text: String) {
        val target = manager ?: return
        val content = ImeKeyEncoder.encodeText(text.replace("\r\n", "\n"))
        val bytes = if (terminalBuffer?.let { it.read { it.bracketedPaste } } == true) {
            "\u001B[200~".toByteArray() + content + "\u001B[201~".toByteArray()
        } else content
        target.write(bytes)
        liveRequest++
        selectionClearKey++
        selectedText = null
    }

    pendingPaste?.let { text ->
        AlertDialog(
            onDismissRequest = { pendingPaste = null },
            title = { Text("Paste multiple lines?") },
            text = { Text("Line breaks may run commands on the remote system.") },
            confirmButton = {
                TextButton(onClick = {
                    sendPaste(text)
                    pendingPaste = null
                }) { Text("Paste") }
            },
            dismissButton = {
                TextButton(onClick = { pendingPaste = null }) { Text("Cancel") }
            }
        )
    }

    session?.remoteOptions?.let { options ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRemoteSessions,
            title = { Text("Remote sessions") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    options.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (!options.tmuxAvailable && !options.screenAvailable && options.error == null) {
                        Text("Neither tmux nor Screen is installed on this host.")
                    }
                    options.sessions.forEach { entry ->
                        val tool = if (entry.tool == RemoteSessionModes.TMUX) "tmux" else "Screen"
                        val state = if (entry.attached) "attached" else "detached"
                        TextButton(onClick = { viewModel.attachRemoteSession(entry) }) {
                            Text("$tool: ${entry.name} ($state)")
                        }
                    }
                    if (options.tmuxAvailable) {
                        TextButton(onClick = { viewModel.createRemoteSession(RemoteSessionModes.TMUX) }) {
                            Text("New tmux session")
                        }
                    }
                    if (options.screenAvailable) {
                        TextButton(onClick = { viewModel.createRemoteSession(RemoteSessionModes.SCREEN) }) {
                            Text("New Screen session")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissRemoteSessions) { Text("Use shell") }
            }
        )
    }

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
                    if (scrollbackOffset > 0) {
                        TextButton(onClick = { liveRequest++ }) { Text("Live") }
                    }
                    if (manager != null) {
                        Box {
                            IconButton(onClick = { moreOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Terminal actions")
                            }
                            DropdownMenu(
                                expanded = moreOpen,
                                onDismissRequest = { moreOpen = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Remote sessions") },
                                    onClick = {
                                        moreOpen = false
                                        viewModel.checkRemoteSessions()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Remote files") },
                                    onClick = {
                                        moreOpen = false
                                        onFiles()
                                    }
                                )
                                if (selectedText != null) {
                                    DropdownMenuItem(
                                        text = { Text("Copy selection") },
                                        onClick = {
                                            clipboard.setPrimaryClip(
                                                ClipData.newPlainText("Terminal text", selectedText.orEmpty())
                                            )
                                            selectionClearKey++
                                            selectedText = null
                                            moreOpen = false
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Paste") },
                                    onClick = {
                                        moreOpen = false
                                        val text = clipboard.primaryClip?.getItemAt(0)
                                            ?.coerceToText(context)?.toString().orEmpty()
                                        if (text.contains('\n') || text.contains('\r')) {
                                            pendingPaste = text
                                        } else if (text.isNotEmpty()) {
                                            sendPaste(text)
                                        }
                                    }
                                )
                            }
                        }
                    }
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
                    scrollToLiveKey = liveRequest,
                    clearSelectionKey = selectionClearKey,
                    onScrollOffsetChanged = { scrollbackOffset = it },
                    onSelectionChanged = { selectedText = it?.takeIf { text -> text.isNotEmpty() } },
                    onBuffer = { terminalBuffer = it },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
            TerminalImeInput(
                onText = {
                    if (it.contains('\n') || it.contains('\r')) {
                        pendingPaste = it
                    } else {
                        liveRequest++
                        selectionClearKey++
                        manager?.write(
                            ImeKeyEncoder.encodeText(it, modifiers.ctrl, modifiers.alt)
                        )
                    }
                    modifiers.clear()
                },
                onKey = { key ->
                    val bytes = ImeKeyEncoder.encode(key, modifiers.ctrl, modifiers.alt)
                    if (bytes != null) {
                        liveRequest++
                        selectionClearKey++
                        manager?.write(bytes)
                        modifiers.clear()
                        true
                    } else {
                        false
                    }
                },
                onDelete = { n ->
                    liveRequest++
                    selectionClearKey++
                    manager?.write(ByteArray(n) { 0x7F })
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .graphicsLayer(alpha = 0f),
                onReady = { imeEditText = it }
            )
            JuiceSshKeyboardBar(
                modifiers = modifiers,
                onSend = {
                    liveRequest++
                    selectionClearKey++
                    manager?.write(it)
                },
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
