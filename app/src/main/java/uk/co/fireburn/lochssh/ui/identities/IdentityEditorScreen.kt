package uk.co.fireburn.lochssh.ui.identities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import uk.co.fireburn.lochssh.data.db.AuthTypes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentityEditorScreen(
    identityId: Long,
    onBack: () -> Unit,
    viewModel: IdentityEditorViewModel = hiltViewModel()
) {
    LaunchedEffect(identityId) { viewModel.load(identityId) }

    var name by remember { mutableStateOf("") }
    var authType by remember { mutableStateOf(AuthTypes.PASSWORD) }
    var keyPath by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }

    LaunchedEffect(viewModel.loaded) {
        if (!viewModel.loaded) return@LaunchedEffect
        viewModel.identity?.let {
            name = it.name
            authType = it.authType
            keyPath = it.keyPath ?: ""
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
                        enabled = name.isNotBlank(),
                        onClick = {
                            viewModel.save(name, authType, keyPath, secret)
                            onBack()
                        }
                    ) { Text("Save") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
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
                OutlinedTextField(
                    value = keyPath,
                    onValueChange = { keyPath = it },
                    label = { Text("Private key path") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
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
