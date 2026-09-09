package com.sabimoni.feature.groups

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import com.sabimoni.core.data.model.MoneyGroup
import com.sabimoni.core.money.format

@Composable
fun GroupsScreen(viewModel: GroupsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GroupsContent(state = state, onCreateGroup = viewModel::createGroup)
}

@Composable
private fun GroupsContent(
    state: GroupsUiState,
    onCreateGroup: (String) -> Unit,
) {
    var newGroup by rememberSaveable { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        Text(
            text = "Groups",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Text(
            text = "Outstanding: ${state.totalOutstanding.format()}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items = state.groups, key = { it.id }) { group ->
                GroupCard(group)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = newGroup,
                onValueChange = { newGroup = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Church, choir, charity…") },
                singleLine = true,
            )
            Button(
                onClick = {
                    onCreateGroup(newGroup)
                    newGroup = ""
                },
                enabled = newGroup.isNotBlank(),
            ) {
                Text("Add")
            }
        }
    }
}

@Composable
private fun GroupCard(group: MoneyGroup) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = group.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = group.penalty?.let { "Penalty if missed: ${it.format()}" }
                    ?: "No penalty recorded",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
