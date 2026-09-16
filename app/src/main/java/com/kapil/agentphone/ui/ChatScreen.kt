package com.kapil.agentphone.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kapil.agentphone.data.BridgeClient

@Composable
fun ChatScreen(sessionId: String, modifier: Modifier = Modifier) {
    val messages by BridgeClient.messages.collectAsStateWithLifecycle()
    val mine = messages.filter { it.sessionId == sessionId }
    var text by rememberSaveable(sessionId) { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(mine.size) {
        if (mine.isNotEmpty()) listState.scrollToItem(mine.size - 1)
    }
    Column(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 8.dp),
            state = listState
        ) {
            items(mine, key = { it.id }) { m ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (m.mine) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            m.text.trimEnd(),
                            Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                text, { text = it }, Modifier.weight(1f),
                placeholder = { Text("Message agent…") }, maxLines = 4
            )
            IconButton(onClick = {
                if (text.isNotBlank()) {
                    BridgeClient.input(sessionId, text)
                    text = ""
                }
            }) {
                Icon(Icons.Filled.Send, "Send")
            }
        }
    }
}
