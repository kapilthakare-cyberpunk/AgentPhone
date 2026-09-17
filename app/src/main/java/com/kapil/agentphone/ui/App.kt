package com.kapil.agentphone.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kapil.agentphone.data.BridgeClient
import com.kapil.agentphone.data.ConnState
import com.kapil.agentphone.util.CrashLog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(initialCrash: String? = null) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var chatId by rememberSaveable { mutableStateOf<String?>(null) }
    var crash by rememberSaveable { mutableStateOf(initialCrash) }
    val approvals by BridgeClient.approvals.collectAsStateWithLifecycle()
    BackHandler(enabled = chatId != null) { chatId = null }
    crash?.let { report ->
        val clipboard = LocalClipboardManager.current
        val ctx = LocalContext.current
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Last run crashed") },
            text = {
                Text(
                    report.take(2000),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                TextButton(onClick = { clipboard.setText(AnnotatedString(report)) }) {
                    Text("Copy log")
                }
            },
            dismissButton = {
                TextButton(onClick = { CrashLog.clear(ctx); crash = null }) {
                    Text("Dismiss")
                }
            }
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (chatId != null) "Chat" else "AgentPhone") },
                navigationIcon = {
                    if (chatId != null) {
                        IconButton(onClick = { chatId = null }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = { ConnDot() }
            )
        },
        bottomBar = {
            if (chatId == null) {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { Icon(Icons.Filled.List, null) },
                        label = { Text("Sessions") }
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (approvals.isNotEmpty()) Badge { Text("${approvals.size}") }
                                }
                            ) { Icon(Icons.Filled.Notifications, null) }
                        },
                        label = { Text("Approvals") }
                    )
                    NavigationBarItem(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        icon = { Icon(Icons.Filled.Settings, null) },
                        label = { Text("Link") }
                    )
                }
            }
        }
    ) { padding ->
        when {
            chatId != null -> ChatScreen(
                sessionId = chatId!!,
                modifier = Modifier.padding(padding)
            )
            tab == 0 -> SessionsScreen(
                onOpen = { chatId = it },
                modifier = Modifier.padding(padding)
            )
            tab == 1 -> ApprovalsScreen(modifier = Modifier.padding(padding))
            else -> SettingsScreen(modifier = Modifier.padding(padding))
        }
    }
}

@Composable
private fun ConnDot() {
    val state by BridgeClient.state.collectAsStateWithLifecycle()
    val color = when (state) {
        ConnState.Connected -> MaterialTheme.colorScheme.primary
        ConnState.Connecting -> MaterialTheme.colorScheme.tertiary
        ConnState.Disconnected -> MaterialTheme.colorScheme.error
    }
    val label = when (state) {
        ConnState.Connected -> "live"
        ConnState.Connecting -> "…"
        ConnState.Disconnected -> "off"
    }
    Text(
        label,
        color = color,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(end = 16.dp)
    )
}
