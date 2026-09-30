package uk.co.fireburn.lochssh.ui.terminal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uk.co.fireburn.lochssh.ssh.SessionRegistry

@Composable
fun SessionDrawer(
    sessions: List<SessionRegistry.Session>,
    currentId: Long,
    onSelect: (Long) -> Unit,
    onHosts: () -> Unit,
    content: @Composable (open: () -> Unit) -> Unit
) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = drawer.isOpen,
        drawerContent = {
            ModalDrawerSheet {
                Text("Sessions", Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = {
                    onHosts()
                }) { Text("Manage hosts") }
                HorizontalDivider()
                LazyColumn {
                    items(sessions, key = { it.id }) { session ->
                        NavigationDrawerItem(
                            selected = session.id == currentId,
                            label = {
                                Column {
                                    Text(session.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(session.statusText, maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            onClick = {
                                if (session.id != currentId && sessions.any { it.id == session.id }) {
                                    onSelect(session.id)
                                } else {
                                    scope.launch { drawer.close() }
                                }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    ) { content { scope.launch { drawer.open() } } }
}
