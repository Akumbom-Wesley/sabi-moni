package com.sabimoni.feature.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sabimoni.core.data.model.Contribution
import com.sabimoni.core.data.model.MoneyGroup
import com.sabimoni.core.money.format
import com.sabimoni.core.money.parseMoney
import com.sabimoni.ui.components.DateChoiceRow
import java.time.LocalDate

/**
 * Log an announced contribution (FR4.2) — the quick form half of the requirement, the
 * other half being the chat path through the parser.
 *
 * Date shortcuts look *forwards*, unlike the entry editor's: a contribution is announced
 * for a date that has not arrived yet. A past due date is still allowed, because
 * "choir wanted 5000 by the 15th" gets relayed on the 16th, and the reminder fires
 * immediately in that case rather than being dropped.
 */
@Composable
fun ContributionEditorDialog(
    contribution: Contribution?,
    groups: List<MoneyGroup>,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (ContributionEdit) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var groupId by rememberSaveable(contribution?.id) {
        mutableStateOf(contribution?.groupId ?: groups.firstOrNull()?.id ?: NO_GROUP)
    }
    var amountText by rememberSaveable(contribution?.id) {
        mutableStateOf(contribution?.amount?.format(withCurrency = false).orEmpty())
    }
    var note by rememberSaveable(contribution?.id) {
        mutableStateOf(contribution?.note.orEmpty())
    }
    var epochDay by rememberSaveable(contribution?.id) {
        mutableStateOf((contribution?.dueDate ?: today).toEpochDay())
    }

    val amount = remember(amountText) { parseMoney(amountText) }
    val dueDate = LocalDate.ofEpochDay(epochDay)
    // The group cannot be moved once set: the payment transaction derives its group from
    // this record (ADR-0025), so re-pointing a settled obligation would strand it.
    val groupFixed = contribution != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (contribution == null) "Log a contribution" else "Edit contribution") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (groups.isEmpty()) {
                    Text(
                        text = "Add a group first — a contribution belongs to one.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    GroupPicker(
                        groups = groups,
                        selectedId = groupId,
                        enabled = !groupFixed,
                        onSelect = { groupId = it },
                    )

                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount (FCFA)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = amountText.isNotEmpty() && amount == null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text("Due", style = MaterialTheme.typography.labelMedium)
                    DateChoiceRow(
                        selected = dueDate,
                        shortcuts = listOf(
                            "Today" to today,
                            "Tomorrow" to today.plusDays(1),
                            "In a week" to today.plusWeeks(1),
                        ),
                        onSelect = { epochDay = it.toEpochDay() },
                    )

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Note") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (onDelete != null) {
                        TextButton(onClick = onDelete) {
                            Text(
                                text = "Delete this contribution",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsed = amount ?: return@TextButton
                    onSave(
                        ContributionEdit(
                            id = contribution?.id,
                            groupId = groupId,
                            amount = parsed,
                            dueDate = dueDate,
                            note = note.trim().takeIf(String::isNotEmpty),
                        ),
                    )
                },
                enabled = amount != null && groupId != NO_GROUP,
            ) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun GroupPicker(
    groups: List<MoneyGroup>,
    selectedId: Long,
    enabled: Boolean,
    onSelect: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = groups.firstOrNull { it.id == selectedId }?.name ?: "Choose a group"

    Box {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = selectedName, maxLines = 1)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            groups.forEach { group ->
                DropdownMenuItem(
                    text = { Text(group.name) },
                    onClick = {
                        onSelect(group.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

private const val NO_GROUP = 0L
