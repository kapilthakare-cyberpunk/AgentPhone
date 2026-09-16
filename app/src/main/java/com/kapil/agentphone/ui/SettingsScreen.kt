package com.kapil.agentphone.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kapil.agentphone.data.BridgeClient
import com.kapil.agentphone.data.LinkPrefs
import com.kapil.agentphone.data.Prefs
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val prefs = remember(ctx) { Prefs(ctx) }
    val link by prefs.link.collectAsStateWithLifecycle(initialValue = LinkPrefs("", 9876, ""))
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("9876") }
    var token by rememberSaveable { mutableStateOf("") }
    var loaded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(link) {
        if (!loaded) {
            host = link.host
            port = link.port.toString()
            token = link.token
            loaded = true
        }
    }
    val state by BridgeClient.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxSize().padding(12.dp)) {
        Text("Mac link", style = MaterialTheme.typography.titleMedium)
        Text("Status: $state", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            host, { host = it }, Modifier.fillMaxWidth(),
            label = { Text("Mac IP (LAN or Tailscale)") },
            placeholder = { Text("192.168.1.5") }, singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            port, { port = it }, Modifier.fillMaxWidth(),
            label = { Text("Port") }, singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            token, { token = it }, Modifier.fillMaxWidth(),
            label = { Text("Token (~/.agentbridge/token)") }, singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                scope.launch {
                    prefs.save(host.trim(), port.toIntOrNull() ?: 9876, token.trim())
                    BridgeClient.reconnectSoon()
                }
            },
            Modifier.fillMaxWidth()
        ) { Text("Save & reconnect") }
        Spacer(Modifier.height(8.dp))
        Text(
            "On your Mac run: agentbridge serve — then enter the printed IP. " +
                "Away from home, use your Tailscale 100.x IP instead.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}
