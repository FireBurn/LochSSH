package uk.co.fireburn.lochssh.ui.identities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.fireburn.lochssh.data.db.IdentityEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentityListScreen(
    onEdit: (Long) -> Unit,
    onNew: () -> Unit,
    onBack: () -> Unit,
    viewModel: IdentityListViewModel = hiltViewModel()
) {
    val identities by viewModel.identities.collectAsStateWithLifecycle(
        emptyList(),
        LocalLifecycleOwner.current.lifecycle
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Identities") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNew) {
                Icon(Icons.Filled.Add, contentDescription = "New identity")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(identities, key = { it.id }) { identity ->
                IdentityRow(
                    identity = identity,
                    onEdit = { onEdit(identity.id) },
                    onDelete = { viewModel.delete(identity) }
                )
            }
        }
    }
}

@Composable
private fun IdentityRow(
    identity: IdentityEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(identity.name, style = MaterialTheme.typography.titleMedium)
                val subtitle = when (identity.authType) {
                    "PUBLIC_KEY" -> identity.keyPath ?: "key"
                    "PASSWORD" -> "password"
                    else -> "no auth"
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Actions")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menuOpen = false; onEdit() })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menuOpen = false; onDelete() })
                }
            }
        }
    }
}
