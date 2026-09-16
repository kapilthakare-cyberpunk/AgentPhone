package com.kapil.agentphone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kapil.agentphone.data.BridgeClient
import com.kapil.agentphone.data.ConnState

@Composable
fun SessionsScreen(onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val sessions by BridgeClient.sessions.collectAsStateWithLifecycle()
    val state by BridgeClient.state.collectAsStateWithLifecycle()
    var showNew by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Sessions", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { BridgeClient.refresh() }) {
                Icon(Icons.Filled.Refresh, "Refresh")
            }
        }
        if (state != ConnState.Connected) {
            Text(
                "Not connected — check the Link tab.",
                color = MaterialTheme.colorScheme.error
            )
        }
        LazyColumn(Modifier.weight(1f)) {
            items(sessions, key = { it.id }) { s ->
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .clickable { onOpen(s.id) }
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(10.dp).background(
                                if (s.status == "running") MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.secondary,
                                CircleShape
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.command, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                            Text("${s.id} · ${s.cwd}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (s.status == "running") {
                            IconButton(onClick = { BridgeClient.kill(s.id) }) {
                                Icon(Icons.Filled.Close, "Kill")
                            }
                        } else {
                            Text("exit ${s.exitCode ?: "?"}")
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { showNew = true }, Modifier.fillMaxWidth()) {
            Text("New session")
        }
    }
    if (showNew) NewSessionDialog(onDismiss = { showNew = false })
}

@Composable
private fun NewSessionDialog(onDismiss: () -> Unit) {
    var command by rememberSaveable { mutableStateOf("") }
    var cwd by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New agent session") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    command, { command = it }, Modifier.fillMaxWidth(),
                    label = { Text("Command") }, placeholder = { Text("claude") },
                    singleLine = true
                )
                OutlinedTextField(
                    cwd, { cwd = it }, Modifier.fillMaxWidth(),
                    label = { Text("Working dir (blank = home)") },
                    placeholder = { Text("~/projects") }, singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (command.isNotBlank()) BridgeClient.spawn(command.trim(), cwd.trim())
                    onDismiss()
                }
            ) { Text("Launch") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
