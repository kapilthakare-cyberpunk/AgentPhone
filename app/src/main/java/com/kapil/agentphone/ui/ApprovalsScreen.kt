package com.kapil.agentphone.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kapil.agentphone.data.BridgeClient

@Composable
fun ApprovalsScreen(modifier: Modifier = Modifier) {
    val approvals by BridgeClient.approvals.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize().padding(12.dp)) {
        Text("Approvals", style = MaterialTheme.typography.titleMedium)
        if (approvals.isEmpty()) {
            Text(
                "Nothing waiting — requests appear here and as notifications.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        LazyColumn(Modifier.weight(1f)) {
            items(approvals, key = { it.rid }) { a ->
                var expanded by rememberSaveable(a.rid) { mutableStateOf(false) }
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(a.title, style = MaterialTheme.typography.bodyLarge)
                        if (a.session.isNotBlank()) {
                            Text(
                                "session ${a.session}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Text(
                            a.detail.ifBlank { "(no detail)" },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = if (expanded) Int.MAX_VALUE else 4
                        )
                        Row {
                            TextButton(onClick = { expanded = !expanded }) {
                                Text(if (expanded) "Less" else "More")
                            }
                            Spacer(Modifier.weight(1f))
                            OutlinedButton(onClick = { BridgeClient.verdict(a.rid, false) }) {
                                Text("Deny")
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = { BridgeClient.verdict(a.rid, true) }) {
                                Text("Approve")
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Unanswered requests deny themselves after the hook timeout.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}
