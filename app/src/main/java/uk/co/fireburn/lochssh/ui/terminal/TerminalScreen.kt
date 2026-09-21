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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import uk.co.fireburn.lochssh.service.SshForegroundService
import uk.co.fireburn.lochssh.ssh.ActiveConnection
import uk.co.fireburn.lochssh.ssh.SshConnectionManager
import uk.co.fireburn.lochssh.ui.keyboard.ImeKeyEncoder
import uk.co.fireburn.lochssh.ui.keyboard.JuiceSshKeyboardBar
import uk.co.fireburn.lochssh.ui.keyboard.TerminalEditText
import uk.co.fireburn.lochssh.ui.keyboard.TerminalImeInput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    hostId: Long,
    onBack: () -> Unit,
    viewModel: TerminalViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val imm = remember {
        context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }
    val managerState = remember { mutableStateOf<SshConnectionManager?>(null) }
    val manager by managerState
    val errorState = remember { mutableStateOf<String?>(null) }
    val attempt = remember { mutableIntStateOf(0) }
    var imeVisible by remember { mutableStateOf(false) }
    var imeEditText by remember { mutableStateOf<TerminalEditText?>(null) }

    LaunchedEffect(hostId, attempt.intValue) {
        viewModel.load(hostId)
        errorState.value = null
        while (true) {
            val m = ActiveConnection.manager
            if (m != null) {
                managerState.value = m
                break
            }
            val err = ActiveConnection.lastError
            if (err != null) {
                errorState.value = err
                break
            }
            delay(100)
        }
    }

    // Surface a dropped session while the screen is open.
    LaunchedEffect(manager) {
        val m = manager ?: return@LaunchedEffect
        while (m.isConnected) delay(500)
        errorState.value = ActiveConnection.lastError ?: "Connection closed"
    }

    Scaffold(
        // Only the top inset: the content pads itself against whichever of the
        // keyboard or the navigation bar is taller, so the bar sits on the keyboard.
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = { Text(viewModel.hostName.ifBlank { "Terminal" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        context.stopService(Intent(context, SshForegroundService::class.java))
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
            val error = errorState.value
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
                    TextButton(onClick = { attempt.intValue++ }) { Text("Retry") }
                }
            } else {
                TerminalView(
                    manager = manager,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
            TerminalImeInput(
                onText = { manager?.write(ImeKeyEncoder.encodeText(it)) },
                onKey = { key ->
                    val bytes = ImeKeyEncoder.encode(key)
                    if (bytes != null) {
                        manager?.write(bytes)
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
                onSend = { manager?.write(it) },
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
