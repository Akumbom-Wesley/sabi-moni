package com.sabimoni.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsContent(
        state = state,
        onSaveApiKey = viewModel::saveApiKey,
        onClearApiKey = viewModel::clearApiKey,
    )
}

@Composable
private fun SettingsContent(
    state: SettingsUiState,
    onSaveApiKey: (String) -> Unit,
    onClearApiKey: () -> Unit,
) {
    // Never seeded from the stored key — it is write-only from the UI's perspective.
    var keyInput by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineSmall)

        Text(text = "AI parsing key", style = MaterialTheme.typography.titleMedium)
        Text(
            text = if (state.hasApiKey) {
                "A Gemini API key is stored encrypted on this device."
            } else {
                "No key yet. Paste a Google AI Studio key to enable interpretation."
            },
            style = MaterialTheme.typography.bodySmall,
        )

        OutlinedTextField(
            value = keyInput,
            onValueChange = { keyInput = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Google AI Studio API key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    onSaveApiKey(keyInput)
                    keyInput = ""
                },
                enabled = keyInput.isNotBlank(),
            ) {
                Text("Save key")
            }
            OutlinedButton(onClick = onClearApiKey, enabled = state.hasApiKey) {
                Text("Remove key")
            }
        }

        HorizontalDivider()

        Text(text = "MoMo SMS capture", style = MaterialTheme.typography.titleMedium)
        Text(
            text = if (state.smsCaptureSupported) {
                "This build can read MTN MoMo alerts automatically."
            } else {
                "Not available in this build. Use chat or manual entry."
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
