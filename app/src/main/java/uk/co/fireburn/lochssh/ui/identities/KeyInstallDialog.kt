package uk.co.fireburn.lochssh.ui.identities

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import uk.co.fireburn.lochssh.ssh.AuthorizedKeyInstaller
import uk.co.fireburn.lochssh.ssh.GeneratedKey
import uk.co.fireburn.lochssh.ssh.HostKeyReviewRequired
import uk.co.fireburn.lochssh.ssh.HostKeyTrust
import uk.co.fireburn.lochssh.ssh.KeyInstaller
import uk.co.fireburn.lochssh.ssh.SavedHostKey

@Composable
fun KeyInstallDialog(key: GeneratedKey, initialUsername: String, onClose: () -> Unit,
    createInstaller: (Context) -> KeyInstaller = ::AuthorizedKeyInstaller) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val installer = remember { createInstaller(context) }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("22") }
    val username = initialUsername
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var installed by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var review by remember { mutableStateOf<SavedHostKey?>(null) }
    DisposableEffect(installer) { onDispose { installer.close() } }

    fun install() {
        val number = port.toIntOrNull()
        if (number == null || number !in 1..65535) { error = "Enter a port from 1 to 65535"; return }
        val bytes = password.toByteArray(Charsets.UTF_8)
        password = ""
        busy = true
        error = null
        scope.launch {
            try {
                runInterruptible(Dispatchers.IO) { installer.install(host.trim(), number, username.trim(), bytes, key) }
                installed = true
            } catch (e: CancellationException) { throw e
            } catch (e: HostKeyReviewRequired) { review = e.key
            } catch (e: Exception) { error = e.message?.take(300) ?: "Could not install the key"
            } finally { bytes.fill(0); busy = false }
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (installed) "Key installed" else "Install public key") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (installed) {
                    Text("Key login works. Choose this saved identity in the host settings. The server password was not saved.")
                } else {
                    Text("The identity is saved on your phone. Add its public key to ~/.ssh/authorized_keys using the server password once. Existing keys are kept. Direct connections only.")
                    OutlinedTextField(host, { host = it }, enabled = !busy, label = { Text("Server address") }, singleLine = true)
                    OutlinedTextField(port, { port = it }, enabled = !busy, label = { Text("Port") }, singleLine = true)
                    OutlinedTextField(username, {}, enabled = !busy, readOnly = true,
                        label = { Text("Server username") }, singleLine = true)
                    OutlinedTextField(password, { password = it }, enabled = !busy,
                        label = { Text("Server password (not saved)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        visualTransformation = PasswordVisualTransformation())
                    if (busy) Text("Installing and checking key login…")
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            if (installed) TextButton(onClick = onClose) { Text("Done") }
            else TextButton(enabled = !busy && host.isNotBlank() && username.isNotBlank() && password.isNotEmpty(),
                onClick = ::install) { Text("Install key") }
        },
        dismissButton = { if (!installed) TextButton(onClick = onClose) { Text("Cancel") } }
    )
    review?.let { candidate ->
        AlertDialog(
            onDismissRequest = { review = null }, title = { Text("Check the server key") },
            text = { Column { Text(candidate.host); Text(candidate.type); Text(candidate.fingerprint)
                Text("Check this fingerprint with the server owner before trusting it. You'll need to enter the password again.") } },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try { runInterruptible(Dispatchers.IO) { HostKeyTrust.accept(context, candidate) }
                            review = null
                        } catch (e: CancellationException) { throw e
                        } catch (e: Exception) { error = e.message; review = null }
                    }
                }) { Text("Trust server key") }
            },
            dismissButton = { TextButton(onClick = { review = null }) { Text("Cancel") } }
        )
    }
}
