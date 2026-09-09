package com.sabimoni.feature.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabimoni.core.data.entity.Direction
import com.sabimoni.core.data.entity.MessageSource
import com.sabimoni.core.data.entity.ParseStatus
import com.sabimoni.core.data.model.CapturedMessage
import com.sabimoni.core.data.model.ParsedLine
import com.sabimoni.core.money.format

@Composable
fun CaptureScreen(viewModel: CaptureViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CaptureContent(state = state, onSend = viewModel::send)
}

@Composable
private fun CaptureContent(
    state: CaptureUiState,
    onSend: (String) -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        Text(
            text = "Today",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )

        if (state.pendingCount > 0) {
            Text(
                text = "${state.pendingCount} waiting to be interpreted",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items = state.messages, key = { it.id }) { message ->
                MessageCard(message)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("taxi 500, lunch 1500, gave 5000 to choir") },
                maxLines = 5,
            )
            FilledIconButton(
                onClick = {
                    onSend(draft)
                    draft = ""
                },
                enabled = draft.isNotBlank(),
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
private fun MessageCard(message: CapturedMessage) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (message.status) {
                ParseStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
                ParseStatus.PENDING_PARSE -> MaterialTheme.colorScheme.surfaceVariant
                ParseStatus.PARSED -> MaterialTheme.colorScheme.primaryContainer
            },
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = message.rawText, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = statusLabel(message),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            message.lineItems.forEach { line -> ParsedLineRow(line) }
        }
    }
}

/** One thing the parser understood, as its own line — the confirmation of FR1.3. */
@Composable
private fun ParsedLineRow(line: ParsedLine) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = line.signedAmount(),
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = line.describe(),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun ParsedLine.signedAmount(): String {
    val sign = if (direction == Direction.EXPENSE) "-" else "+"
    return sign + amount.format()
}

private fun ParsedLine.describe(): String =
    listOfNotNull(category, note).joinToString(" · ").ifEmpty { "uncategorised" }

private fun statusLabel(message: CapturedMessage): String {
    val prefix = if (message.source == MessageSource.SMS) "MoMo SMS · " else ""
    return prefix + when (message.status) {
        ParseStatus.PENDING_PARSE -> "waiting to be interpreted"
        ParseStatus.PARSED -> when (val count = message.lineItems.size) {
            0 -> "nothing to log here"
            1 -> "understood 1 entry"
            else -> "understood $count entries"
        }
        ParseStatus.FAILED -> message.failureReason ?: "could not be interpreted"
    }
}
