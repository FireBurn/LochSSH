package uk.co.fireburn.lochssh.ui.hosts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import uk.co.fireburn.lochssh.data.db.AuthTypes
import uk.co.fireburn.lochssh.data.db.IdentityEntity
import uk.co.fireburn.lochssh.ssh.SavedSshProxy
import uk.co.fireburn.lochssh.ssh.SshProxyType

@Composable
fun HostProxySettings(fields: HostProxyFields, identities: List<IdentityEntity>,
    onChange: (HostProxyFields) -> Unit, error: String?) {
    var menu by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Connection proxy", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(SavedSshProxy.NONE to "None", SshProxyType.HTTP.name to "HTTP CONNECT",
                SshProxyType.SOCKS5.name to "SOCKS5").forEach { (type, label) ->
                FilterChip(selected = fields.type == type, label = { Text(label) }, onClick = {
                    onChange(if (type == SavedSshProxy.NONE) HostProxyFields() else fields.copy(type = type))
                })
            }
        }
        if (fields.type != SavedSshProxy.NONE) {
            OutlinedTextField(fields.host, { onChange(fields.copy(host = it)) },
                label = { Text("Proxy address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(fields.port, { onChange(fields.copy(port = it)) },
                label = { Text("Proxy port") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            val identity = identities.find { it.id == fields.identityId }
            Text("Proxy login: ${identity?.name ?: if (fields.identityId == null) "None" else "Missing identity"}")
            TextButton(onClick = { menu = true }) { Text("Choose proxy identity") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("None (no proxy login)") }, onClick = {
                    onChange(fields.copy(identityId = null)); menu = false
                })
                identities.filter { it.authType == AuthTypes.PASSWORD }.forEach { selected ->
                    DropdownMenuItem(text = { Text(selected.name) }, onClick = {
                        onChange(fields.copy(identityId = selected.id)); menu = false
                    })
                }
            }
            Text("Optional: create a separate password identity in Identities. Its password stays in encrypted storage. " +
                "Proxy password exchanges are not encrypted; use a trusted network or VPN. " +
                "For jump hosts, set the proxy on the first jump host. Port knocking is not supported through proxies.",
                style = MaterialTheme.typography.bodySmall)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
fun ProxyLoginWarning(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Save this proxy login?") },
        text = { Text("The proxy password is sent without encryption between your phone and the proxy. " +
            "SSH does not protect this exchange. Only use a trusted network or VPN.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
