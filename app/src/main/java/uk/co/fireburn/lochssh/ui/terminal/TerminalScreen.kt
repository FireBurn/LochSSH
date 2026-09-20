package uk.co.fireburn.lochssh.ui.terminal

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import uk.co.fireburn.lochssh.service.SshForegroundService
import uk.co.fireburn.lochssh.ssh.ActiveConnection
import uk.co.fireburn.lochssh.ssh.SshConnectionManager
import uk.co.fireburn.lochssh.ui.keyboard.JuiceSshKeyboardBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    hostId: Long,
    onBack: () -> Unit,
    viewModel: TerminalViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val managerState = remember { mutableStateOf<SshConnectionManager?>(null) }
    val manager by managerState
    val errorState = remember { mutableStateOf<String?>(null) }
    val attempt = remember { mutableIntStateOf(0) }
    var imeVisible by remember { mutableStateOf(false) }

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
                .imePadding()
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
            JuiceSshKeyboardBar(
                onSend = { manager?.write(it) },
                onImeToggle = {
                    imeVisible = !imeVisible
                    if (imeVisible) keyboardController?.show() else keyboardController?.hide()
                }
            )
        }
    }
}
