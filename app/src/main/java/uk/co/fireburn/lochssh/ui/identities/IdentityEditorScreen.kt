package uk.co.fireburn.lochssh.ui.identities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import uk.co.fireburn.lochssh.data.db.AuthTypes
import uk.co.fireburn.lochssh.ssh.GeneratedKey
import uk.co.fireburn.lochssh.ssh.KeyGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentityEditorScreen(
    identityId: Long,
    onBack: () -> Unit,
    viewModel: IdentityEditorViewModel = hiltViewModel()
) {
    LaunchedEffect(identityId) { viewModel.load(identityId) }

    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var authType by remember { mutableStateOf(AuthTypes.PASSWORD) }
    var keySource by remember { mutableStateOf(KeySources.PATH) }
    var keyPath by remember { mutableStateOf("") }
    var keyMaterial by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var generatedKey by remember { mutableStateOf<GeneratedKey?>(null) }
    var showPubKey by remember { mutableStateOf(false) }
    var genError by remember { mutableStateOf<String?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(viewModel.loaded) {
        if (!viewModel.loaded) return@LaunchedEffect
        viewModel.identity?.let {
            name = it.name
            username = it.username
            authType = it.authType
            keyPath = it.keyPath ?: ""
            keySource = if (viewModel.hasKeyMaterial) KeySources.PASTE else KeySources.PATH
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (identityId == 0L) "New identity" else "Edit identity") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        enabled = viewModel.loaded && !saving && name.isNotBlank(),
                        onClick = {
                            saving = true
                            saveError = null
                            viewModel.save(name, username, authType, keySource, keyPath, keyMaterial, secret)
                                .invokeOnCompletion { cause ->
                                    saving = false
                                    if (cause == null) onBack()
                                    else saveError = cause.message ?: "Could not save identity"
                                }
                        }
                    ) { Text("Save") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            saveError?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(AuthTypes.PASSWORD, AuthTypes.PUBLIC_KEY, AuthTypes.NONE).forEach { type ->
                    FilterChip(
                        selected = authType == type,
                        onClick = { authType = type },
                        label = { Text(type) }
                    )
                }
            }
            if (authType == AuthTypes.PUBLIC_KEY) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = keySource == KeySources.PATH,
                        onClick = { keySource = KeySources.PATH },
                        label = { Text("File path") }
                    )
                    FilterChip(
                        selected = keySource == KeySources.PASTE,
                        onClick = { keySource = KeySources.PASTE },
                        label = { Text("Pasted key") }
                    )
                }
                if (keySource == KeySources.PATH) {
                    OutlinedTextField(
                        value = keyPath,
                        onValueChange = { keyPath = it },
                        label = { Text("Private key path") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = keyMaterial,
                        onValueChange = { keyMaterial = it },
                        label = { Text("Private key (paste)") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = {
                        try {
                            val gen = KeyGenerator.generate()
                            keyMaterial = gen.privateKeyPem
                            generatedKey = gen
                            showPubKey = true
                        } catch (e: Exception) {
                            genError = e.message ?: "Key generation failed"
                        }
                    }) { Text("Generate key pair") }
                }
            }
            if (authType != AuthTypes.NONE) {
                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = {
                        Text(
                            if (identityId == 0L) "Password / passphrase"
                            else "New password / passphrase (blank to keep)"
                        )
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    val gen = generatedKey
    if (showPubKey && gen != null) {
        AlertDialog(
            onDismissRequest = { showPubKey = false },
            title = { Text("Public key") },
            text = {
                Column {
                    Text(
                        "Add this to the server's authorized_keys:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(gen.publicKeyOpenSsh, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(gen.publicKeyOpenSsh))
                }) { Text("Copy") }
            },
            dismissButton = {
                TextButton(onClick = { showPubKey = false }) { Text("Close") }
            }
        )
    }

    val err = genError
    if (err != null) {
        AlertDialog(
            onDismissRequest = { genError = null },
            title = { Text("Key generation failed") },
            text = { Text(err) },
            confirmButton = {
                TextButton(onClick = { genError = null }) { Text("OK") }
            }
        )
    }
}
