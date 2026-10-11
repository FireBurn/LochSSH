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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import uk.co.fireburn.lochssh.ssh.RecordingCoordinator
import uk.co.fireburn.lochssh.service.SshForegroundService
import uk.co.fireburn.lochssh.data.db.RemoteSessionModes
import uk.co.fireburn.lochssh.ssh.RemoteSessions
import uk.co.fireburn.lochssh.ssh.RemoteSessionConnections
import uk.co.fireburn.lochssh.ui.keyboard.ImeKeyEncoder
import uk.co.fireburn.lochssh.ui.keyboard.JuiceSshKeyboardBar
import uk.co.fireburn.lochssh.ui.keyboard.TerminalEditText
import uk.co.fireburn.lochssh.ui.keyboard.TerminalImeInput
import uk.co.fireburn.lochssh.ui.settings.BatterySettingsCard

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
    val sessionShortcuts by viewModel.sessionShortcuts.collectAsStateWithLifecycle()
    val terminalTypeface by viewModel.terminalTypeface.collectAsStateWithLifecycle()
    val wrapLines by viewModel.wrapLines.collectAsStateWithLifecycle()
    val terminalTheme by viewModel.terminalTheme.collectAsStateWithLifecycle()
    val keyboardLayout by viewModel.keyboardLayout.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val replacements by viewModel.replacements.collectAsStateWithLifecycle()
    val imm = remember {
        context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }
    val clipboard = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
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
    var batterySettingsOpen by remember { mutableStateOf(false) }
    var performanceOpen by remember(sessionId) { mutableStateOf(false) }
    var pendingLinks by remember(sessionId) { mutableStateOf<List<String>>(emptyList()) }
    var linkError by remember(sessionId) { mutableStateOf<String?>(null) }
    val selectedLinks = remember(selectedText) { TerminalLinks.find(selectedText.orEmpty()) }
    var confirmExport by remember(sessionId) { mutableStateOf(false) }
    val exportError by viewModel.exportError.collectAsStateWithLifecycle()
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) {
        viewModel.saveTranscript(it)
    }

    val session = sessions.firstOrNull { it.id == sessionId }
    val savedCommands by viewModel.savedCommands.collectAsStateWithLifecycle()
    val manager = session?.manager
    var splitId by remember(sessionId) { mutableStateOf<Long?>(null) }
    var splitFocused by remember(sessionId) { mutableStateOf(false) }
    var splitPicker by remember(sessionId) { mutableStateOf(false) }
    val splitSession = splitId?.takeIf { it != sessionId }?.let { id -> sessions.firstOrNull { it.id == id } }
    val splitManager = splitSession?.manager?.takeIf { it.isConnected }
    val inputManager = if (splitFocused && splitManager != null) splitManager else manager
    val inputId = if (inputManager === splitManager && splitManager != null) splitSession!!.id else sessionId
    var recordingsOpen by remember { mutableStateOf(false) }
    val recordingExportError by viewModel.recordingExportError.collectAsStateWithLifecycle()
    val recordingPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {
        viewModel.saveRecording(it)
    }
    if (recordingsOpen) {
        RecordingsDialog(viewModel.recordings, onExport = {
            viewModel.prepareRecording(it)
            recordingPicker.launch("lochssh-${it.id}.terminal")
        }, onDismiss = { recordingsOpen = false })
    }
    recordingExportError?.let { message ->
        AlertDialog(onDismissRequest = viewModel::clearRecordingExportError,
            title = { Text("Could not export recording") }, text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::clearRecordingExportError) { Text("OK") } })
    }
    val activeRecordings by viewModel.recordings.recordings.collectAsStateWithLifecycle()
    var lastRecording by remember(manager) { mutableStateOf<RecordingCoordinator.Recording?>(null) }
    val recording = activeRecordings[manager?.recording] ?: lastRecording
    var confirmRecording by remember(manager) { mutableStateOf(false) }
    var startingRecording by remember(manager) { mutableStateOf(false) }
    var recordingError by remember(manager) { mutableStateOf<String?>(null) }
    val recordingScope = rememberCoroutineScope()
    LaunchedEffect(activeRecordings, manager) {
        activeRecordings[manager?.recording]?.let { lastRecording = it }
    }
    if (confirmRecording && manager != null) {
        AlertDialog(onDismissRequest = { confirmRecording = false },
            title = { Text("Start recording?") },
            text = { Text("Save raw output from this connection in private app storage. Output may contain passwords, tokens and private text. Recording stops when this connection closes and does not restart on reconnect.") },
            confirmButton = { TextButton(onClick = {
                confirmRecording = false
                startingRecording = true
                recordingError = null
                recordingScope.launch {
                    try { lastRecording = viewModel.recordings.start(manager, session.hostName) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { recordingError = "Could not start recording" }
                    finally { startingRecording = false }
                }
            }) { Text("Start recording") } },
            dismissButton = { TextButton(onClick = { confirmRecording = false }) { Text("Cancel") } })
    }
    val modifiers = rememberTerminalModifiers(inputId to inputManager)
    if (performanceOpen && manager != null) {
        key(manager) {
            PerformanceDialog(session.hostName, manager::readPerformance) { performanceOpen = false }
        }
    }
    if (batterySettingsOpen) {
        AlertDialog(onDismissRequest = { batterySettingsOpen = false },
            title = { Text("Background connections") },
            text = { BatterySettingsCard() },
            confirmButton = { TextButton(onClick = { batterySettingsOpen = false }) { Text("Close") } })
    }
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
        val target = inputManager ?: return
        val content = ImeKeyEncoder.encodeText(text.replace("\r\n", "\n"))
        val pasteBuffer = target.terminal.buffer
        val bytes = if (pasteBuffer.read { pasteBuffer.bracketedPaste }) {
            "\u001B[200~".toByteArray() + content + "\u001B[201~".toByteArray()
        } else content
        target.write(bytes)
        liveRequest++
        selectionClearKey++
        selectedText = null
    }

    fun openRemoteSession(command: String, label: String? = null) {
        val hostId = session?.hostId ?: return
        val nextId = viewModel.newSessionId()
        viewModel.dismissRemoteSessions()
        SshForegroundService.start(context, hostId, nextId, command, sessionId, label)
        onSwitchSession(nextId)
    }

    fun leave() {
        if (leaving) return
        leaving = true
        onBack()
    }

    fun usesApplicationCursor(): Boolean = inputManager?.terminal?.buffer?.let { buffer ->
        buffer.read { buffer.applicationCursor }
    } == true

    fun usesApplicationKeypad(): Boolean = inputManager?.terminal?.buffer?.let { buffer ->
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
                inputManager?.write(byteArrayOf('\n'.code.toByte()))
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
                        TextButton(onClick = { openRemoteSession(RemoteSessions.attach(entry), entry.name) }) {
                            Text("$tool: ${entry.name} ($state)")
                        }
                    }
                    if (options.sessions.isNotEmpty()) {
                        TextButton(onClick = {
                            val hostId = session?.hostId ?: return@TextButton
                            val connections = RemoteSessionConnections.plan(hostId, options.sessions,
                                sessions, viewModel::newSessionId)
                            viewModel.dismissRemoteSessions()
                            connections.filter { it.connect }.forEach {
                                val entry = options.sessions.first { entry -> RemoteSessions.attach(entry) == it.command }
                                SshForegroundService.start(context, hostId, it.id, it.command, sessionId, entry.name)
                            }
                            connections.firstOrNull()?.id?.let { targetId ->
                                if (connections.none { it.connect }) {
                                    SshForegroundService.replaceShell(context, sessionId, targetId)
                                }
                                onSwitchSession(targetId)
                            }
                        }) { Text("Connect all remote sessions") }
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
    LaunchedEffect(sessionId, session?.id, replacements[sessionId]) {
        replacements[sessionId]?.let { onSwitchSession(it); return@LaunchedEffect }
        if (session != null) hadSession = true
        else if (hadSession) leave()
        else {
            delay(1_000)
            if (viewModel.sessions.value.none { it.id == sessionId }) onHosts()
        }
    }

    if (splitPicker) {
        AlertDialog(onDismissRequest = { splitPicker = false },
            title = { Text("Show beside this session") },
            text = {
                Column {
                    sessions.filter { it.id != sessionId && it.manager?.isConnected == true }.forEach { other ->
                        TextButton(onClick = {
                            splitId = other.id
                            splitFocused = true
                            splitPicker = false
                        }) { Text(other.label.ifBlank { other.hostName }) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { splitPicker = false }) { Text("Cancel") } })
    }

    SessionDrawer(sessions, sessionId, onSwitchSession, onHosts, onReconnectAll = {
        sessions.filter { it.canReconnect }.forEach {
            SshForegroundService.start(context, it.hostId, it.id, it.remoteCommand)
        }
    }) { openSessions ->
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
                                    text = { Text(if (splitManager == null) "Split view" else "Close split view") },
                                    enabled = splitManager != null || sessions.any { it.id != sessionId && it.manager?.isConnected == true },
                                    onClick = {
                                        moreOpen = false
                                        if (splitManager == null) splitPicker = true
                                        else { splitId = null; splitFocused = false }
                                    })
                                DropdownMenuItem(text = { Text("Background connections") }, onClick = {
                                    moreOpen = false
                                    batterySettingsOpen = true
                                })
                                DropdownMenuItem(text = { Text("Performance") },
                                    enabled = session?.isConnected == true,
                                    onClick = { moreOpen = false; performanceOpen = true })
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
                                DropdownMenuItem(text = { Text("Recordings") },
                                    onClick = { moreOpen = false; recordingsOpen = true })
                                DropdownMenuItem(text = { Text(if (activeRecordings.containsKey(manager?.recording)) "Stop recording" else "Start recording") },
                                    enabled = !startingRecording && (activeRecordings.containsKey(manager?.recording) || session?.isConnected == true),
                                    onClick = {
                                        moreOpen = false
                                        val active = activeRecordings[manager?.recording]
                                        if (active != null) active.stop() else confirmRecording = true
                                    })
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
                    RecordingStatus(recording, startingRecording, recordingError)
                    val error = session?.error
                    if (session == null || error != null || (manager != null && !manager.isConnected)) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                error ?: "This connection is no longer available.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                            TextButton(onClick = {
                                session?.let { SshForegroundService.start(context, it.hostId, it.id, it.remoteCommand) }
                                    ?: onHosts()
                            }) { Text(if (session == null) "Manage hosts" else "Reconnect") }
                            if (session != null) {
                                TextButton(onClick = onHosts) { Text("Manage hosts") }
                            }
                        }
                    } else {
                        val primary: @Composable (Modifier) -> Unit = { paneModifier ->
                            TerminalView(
                                manager = manager,
                                fontSize = fontSize.sp,
                                typeface = terminalTypeface,
                                wrapLines = wrapLines,
                                theme = terminalTheme,
                                scrollToLiveKey = liveRequest,
                                clearSelectionKey = selectionClearKey,
                                onScrollOffsetChanged = { scrollbackOffset = it },
                                onSelectionChanged = { selectedText = it?.takeIf { text -> text.isNotEmpty() } },
                                onBuffer = { terminalBuffer = it },
                                onLinkTap = { pendingLinks = listOf(it) },
                                modifier = paneModifier
                            )
                        }
                        if (splitManager == null) {
                            primary(Modifier.weight(1f).fillMaxWidth())
                        } else {
                            val accent = MaterialTheme.colorScheme.primary
                            SplitPanes(
                                horizontal = LocalConfiguration.current.orientation ==
                                    android.content.res.Configuration.ORIENTATION_LANDSCAPE,
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                first = { primary(it.splitFocus(!splitFocused, accent) { splitFocused = false }) },
                                second = {
                                    TerminalView(
                                        manager = splitManager,
                                        fontSize = fontSize.sp,
                                        typeface = terminalTypeface,
                                        wrapLines = wrapLines,
                                        theme = terminalTheme,
                                        modifier = it.splitFocus(splitFocused, accent) { splitFocused = true }
                                    )
                                }
                            )
                        }
                    }
                    TerminalImeInput(
                        onText = {
                            if (it.contains('\n') || it.contains('\r')) {
                                pendingPaste = it
                            } else {
                                liveRequest++
                                selectionClearKey++
                                inputManager?.write(
                                    ImeKeyEncoder.encodeText(it, modifiers.ctrl, modifiers.alt)
                                )
                            }
                            modifiers.clear()
                        },
                        onKey = { key ->
                            val direction = SessionShortcuts.direction(key, sessionShortcuts)
                            if (direction != null) {
                                if (key.repeatCount == 0) {
                                    SessionShortcuts.destination(sessions.map { it.id }, sessionId, direction)
                                        ?.let(onSwitchSession)
                                }
                                true
                            } else {
                                val bytes = ImeKeyEncoder.encode(key, modifiers.ctrl, modifiers.alt,
                                    usesApplicationCursor(), usesApplicationKeypad())
                                if (bytes != null) {
                                    liveRequest++
                                    selectionClearKey++
                                    inputManager?.write(bytes)
                                    modifiers.clear()
                                    true
                                } else {
                                    false
                                }
                            }
                        },
                        onDelete = { n ->
                            liveRequest++
                            selectionClearKey++
                            inputManager?.write(ByteArray(n) { 0x7F })
                            modifiers.clear()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .graphicsLayer(alpha = 0f),
                        onReady = { imeEditText = it }
                    )
                    JuiceSshKeyboardBar(
                        inputTarget = inputId to inputManager,
                        layout = keyboardLayout,
                        applicationCursor = ::usesApplicationCursor,
                        modifiers = modifiers,
                        onSend = {
                            liveRequest++
                            selectionClearKey++
                            inputManager?.write(it)
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
