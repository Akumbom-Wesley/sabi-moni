package com.sabimoni.feature.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import com.sabimoni.core.data.entity.GroupType
import com.sabimoni.core.data.model.MoneyGroup
import com.sabimoni.core.money.format
import com.sabimoni.core.money.parseMoney

private val LEAD_DAY_CHOICES = listOf(1, 2, 3, 7)

/**
 * Create or edit a group (FR4.1): name, type, the penalty for missing a contribution, and
 * how many days ahead to warn.
 *
 * The penalty is optional but load-bearing — it is what the reminder puts in front of you
 * (FR4.4), and the reason this pillar of the app exists. Left empty it simply is not
 * mentioned, rather than being shown as zero.
 */
@Composable
fun GroupEditorDialog(
    group: MoneyGroup?,
    onDismiss: () -> Unit,
    onSave: (GroupEdit) -> Unit,
) {
    var name by rememberSaveable(group?.id) { mutableStateOf(group?.name.orEmpty()) }
    var type by rememberSaveable(group?.id) {
        mutableStateOf(group?.type ?: GroupType.CONTRIBUTION)
    }
    var penaltyText by rememberSaveable(group?.id) {
        mutableStateOf(group?.penalty?.format(withCurrency = false).orEmpty())
    }
    var leadDays by rememberSaveable(group?.id) {
        mutableStateOf(group?.reminderLeadDays ?: DEFAULT_LEAD_DAYS)
    }

    val penalty = remember(penaltyText) { parseMoney(penaltyText) }
    val penaltyInvalid = penaltyText.isNotBlank() && penalty == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (group == null) "New group" else "Edit group") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("Church, choir, charity…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Type", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GroupType.entries.forEach { option ->
                        FilterChip(
                            selected = type == option,
                            onClick = { type = option },
                            label = { Text(option.label, maxLines = 1, softWrap = false) },
                        )
                    }
                }

                OutlinedTextField(
                    value = penaltyText,
                    onValueChange = { penaltyText = it },
                    label = { Text("Penalty if missed (FCFA)") },
                    supportingText = { Text("Optional. Shown in the reminder.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = penaltyInvalid,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Warn me this far ahead", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LEAD_DAY_CHOICES.forEach { days ->
                        FilterChip(
                            selected = leadDays == days,
                            onClick = { leadDays = days },
                            label = {
                                Text(
                                    text = if (days == 1) "1 day" else "$days days",
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        GroupEdit(
                            id = group?.id,
                            name = name.trim(),
                            type = type,
                            penalty = penalty,
                            reminderLeadDays = leadDays,
                        ),
                    )
                },
                enabled = name.isNotBlank() && !penaltyInvalid,
            ) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Wording, not domain — kept next to the chips that show it. */
private val GroupType.label: String
    get() = when (this) {
        GroupType.CONTRIBUTION -> "Contribution"
        GroupType.TONTINE -> "Tontine"
        GroupType.CHARITY -> "Charity"
        GroupType.SCHOOL -> "School"
        GroupType.OTHER -> "Other"
    }

private const val DEFAULT_LEAD_DAYS = 2
