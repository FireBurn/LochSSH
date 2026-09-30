package uk.co.fireburn.lochssh.ui.terminal

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import uk.co.fireburn.lochssh.ssh.RemoteSessions
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
    onSwitchSession: (Long) -> Unit,
    onHosts: () -> Unit,
    viewModel: TerminalViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val fontSize by viewModel.fontSize.collectAsStateWithLifecycle()
    val terminalTypeface by viewModel.terminalTypeface.collectAsStateWithLifecycle()
    val terminalTheme by viewModel.terminalTheme.collectAsStateWithLifecycle()
    val keyboardLayout by viewModel.keyboardLayout.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
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
    var leaving by remember(sessionId) { mutableStateOf(false) }
    var scrollbackOffset by remember(sessionId) { mutableIntStateOf(0) }
    var liveRequest by remember(sessionId) { mutableIntStateOf(0) }
    var selectionClearKey by remember(sessionId) { mutableIntStateOf(0) }
    var selectedText by remember(sessionId) { mutableStateOf<String?>(null) }
    var terminalBuffer by remember(sessionId) { mutableStateOf<TerminalBuffer?>(null) }
    var pendingPaste by remember(sessionId) { mutableStateOf<String?>(null) }
    var moreOpen by remember { mutableStateOf(false) }
    var toolbarVisible by remember(sessionId) { mutableStateOf(true) }
    var savedCommandsOpen by remember { mutableStateOf(false) }
    var pendingLinks by remember(sessionId) { mutableStateOf<List<String>>(emptyList()) }
    var linkError by remember(sessionId) { mutableStateOf<String?>(null) }
    val selectedLinks = remember(selectedText) { TerminalLinks.find(selectedText.orEmpty()) }
    var confirmExport by remember(sessionId) { mutableStateOf(false) }
    val exportError by viewModel.exportError.collectAsStateWithLifecycle()
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) {
        viewModel.saveTranscript(it)
    }

    val session by viewModel.session.collectAsStateWithLifecycle()
    val savedCommands by viewModel.savedCommands.collectAsStateWithLifecycle()
    val manager = session?.manager
    var savingHostKey by remember { mutableStateOf(false) }
    var hostKeyError by remember { mutableStateOf<String?>(null) }
    val reviewedKey = session?.hostKeyReview
    LaunchedEffect(reviewedKey) { hostKeyError = null }
    reviewedKey?.let { key ->
        AlertDialog(onDismissRequest = { if (!savingHostKey) viewModel.dismissHostKeyReview() },
            title = { Text("Trust this server key?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(key.host)
                    Text(key.type)
                    Text(key.fingerprint)
                    Text("Check this fingerprint with the server owner before trusting it. This may be a jump host rather than the destination.")
                    hostKeyError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }, confirmButton = {
                TextButton(enabled = !savingHostKey, onClick = {
                    savingHostKey = true
                    viewModel.acceptHostKey(key) { error ->
                        savingHostKey = false
                        hostKeyError = error
                        if (error == null) session?.let { SshForegroundService.start(context, it.hostId, it.id) }
                    }
                }) { Text("Trust and connect") }
            }, dismissButton = {
                TextButton(enabled = !savingHostKey, onClick = viewModel::dismissHostKeyReview) { Text("Cancel") }
            })
    }

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

    fun openRemoteSession(command: String) {
        val hostId = session?.hostId ?: return
        val nextId = viewModel.newSessionId()
        viewModel.dismissRemoteSessions()
        SshForegroundService.start(context, hostId, nextId, command)
        onSwitchSession(nextId)
    }

    fun leave() {
        if (leaving) return
        leaving = true
        onBack()
    }

    fun usesApplicationCursor(): Boolean = manager?.terminal?.buffer?.let { buffer ->
        buffer.read { buffer.applicationCursor }
    } == true

    fun usesApplicationKeypad(): Boolean = manager?.terminal?.buffer?.let { buffer ->
        buffer.read { buffer.applicationKeypad }
    } == true

    if (confirmExport) {
        AlertDialog(onDismissRequest = { confirmExport = false },
            title = { Text("Save terminal text?") },
            text = { Text("Save the current screen and retained scrollback. This may include secrets. Older discarded output is not included.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmExport = false
                    terminalBuffer?.let {
                        viewModel.prepareTranscript(it)
                        exportPicker.launch("lochssh-terminal.txt")
                    }
                }) { Text("Choose file") }
            }, dismissButton = { TextButton(onClick = { confirmExport = false }) { Text("Cancel") } })
    }
    exportError?.let { message ->
        AlertDialog(onDismissRequest = viewModel::clearExportError,
            title = { Text("Could not save terminal text") }, text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::clearExportError) { Text("OK") } })
    }
    if (pendingLinks.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { pendingLinks = emptyList() },
            title = { Text("Open link in browser?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    pendingLinks.forEach { link ->
                        TextButton(onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).apply {
                                    addCategory(Intent.CATEGORY_BROWSABLE)
                                })
                            }.onFailure { linkError = "Could not open link: ${it.message ?: "no browser available"}" }
                            pendingLinks = emptyList()
                        }) { Text(link) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pendingLinks = emptyList() }) { Text("Cancel") } }
        )
    }
    linkError?.let { message ->
        AlertDialog(onDismissRequest = { linkError = null }, title = { Text("Could not open link") },
            text = { Text(message) }, confirmButton = {
                TextButton(onClick = { linkError = null }) { Text("OK") }
            })
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

    if (savedCommandsOpen) {
        SavedCommandsDialog(
            commands = savedCommands,
            onSave = viewModel::saveCommand,
            onDelete = viewModel::deleteCommand,
            onInsert = ::sendPaste,
            onRun = { command ->
                sendPaste(command)
                manager?.write(byteArrayOf('\n'.code.toByte()))
            },
            onClose = { savedCommandsOpen = false }
        )
    }

    session?.remoteOptions?.takeIf { session?.forwardWarnings.isNullOrEmpty() }?.let { options ->
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
                        TextButton(onClick = { openRemoteSession(RemoteSessions.attach(entry)) }) {
                            Text("$tool: ${entry.name} ($state)")
                        }
                    }
                    if (options.tmuxAvailable) {
                        TextButton(onClick = { openRemoteSession(RemoteSessions.create(RemoteSessionModes.TMUX)) }) {
                            Text("New tmux session")
                        }
                    }
                    if (options.screenAvailable) {
                        TextButton(onClick = { openRemoteSession(RemoteSessions.create(RemoteSessionModes.SCREEN)) }) {
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

    session?.forwardWarnings?.takeIf { it.isNotEmpty() }?.let { warnings ->
        AlertDialog(
            onDismissRequest = viewModel::dismissForwardWarnings,
            title = { Text("Port forwarding issue") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    warnings.forEach { Text(it) }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissForwardWarnings) { Text("OK") }
            }
        )
    }

    LaunchedEffect(sessionId) { viewModel.load(sessionId) }
    LaunchedEffect(toolbarVisible, moreOpen, sessionId) {
        if (toolbarVisible && !moreOpen) {
            delay(8_000)
            toolbarVisible = false
        }
    }
    LaunchedEffect(sessionId, session?.id) {
        if (session != null) hadSession = true
        else if (hadSession) leave()
    }

    SessionDrawer(sessions, sessionId, onSwitchSession, onHosts) { openSessions ->
        Scaffold(
            // Only the top inset: the content pads itself against whichever of the
            // keyboard or the navigation bar is taller, so the bar sits on the keyboard.
            contentWindowInsets = WindowInsets.statusBars,
            topBar = {
                if (toolbarVisible) TopAppBar(
                    title = {
                        Text(
                            session?.label?.ifBlank { null } ?: "Terminal",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { leave() }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (scrollbackOffset > 0) {
                            TextButton(onClick = { liveRequest++ }) { Text("Live") }
                        }
                        Box {
                            IconButton(onClick = { moreOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Terminal actions")
                            }
                            DropdownMenu(
                                expanded = moreOpen,
                                onDismissRequest = { moreOpen = false }
                            ) {
                                DropdownMenuItem(text = { Text("Switch session") }, onClick = {
                                    moreOpen = false
                                    openSessions()
                                })
                                DropdownMenuItem(
                                    text = { Text("Remote sessions") },
                                    enabled = manager != null,
                                    onClick = {
                                        moreOpen = false
                                        viewModel.checkRemoteSessions()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Remote files") },
                                    enabled = manager != null,
                                    onClick = {
                                        moreOpen = false
                                        onFiles()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Saved commands") },
                                    enabled = manager != null,
                                    onClick = {
                                        moreOpen = false
                                        savedCommandsOpen = true
                                    }
                                )
                                DropdownMenuItem(text = { Text("Save terminal text") },
                                    enabled = terminalBuffer != null,
                                    onClick = { moreOpen = false; confirmExport = true })
                                if (selectedText != null) {
                                    if (selectedLinks.isNotEmpty()) {
                                        DropdownMenuItem(text = { Text("Open selected link") }, onClick = {
                                            pendingLinks = selectedLinks
                                            moreOpen = false
                                        })
                                    }
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
                                    enabled = manager != null,
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
                                DropdownMenuItem(text = { Text("Hide controls") }, onClick = {
                                    moreOpen = false
                                    toolbarVisible = false
                                })
                                DropdownMenuItem(text = { Text("Disconnect") }, onClick = {
                                    moreOpen = false
                                    SshForegroundService.disconnect(context, sessionId)
                                    leave()
                                })
                            }
                        }
                    }
                )
            }
        ) { padding ->
            Box(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
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
                            typeface = terminalTypeface,
                            theme = terminalTheme,
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
                            val bytes = ImeKeyEncoder.encode(key, modifiers.ctrl, modifiers.alt,
                                usesApplicationCursor(), usesApplicationKeypad())
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
                        layout = keyboardLayout,
                        applicationCursor = ::usesApplicationCursor,
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
                if (!toolbarVisible) {
                    TextButton(
                        onClick = { toolbarVisible = true },
                        modifier = Modifier.align(Alignment.TopEnd).padding(padding)
                    ) { Text("Show controls") }
                }
            }
        }
    }
}
