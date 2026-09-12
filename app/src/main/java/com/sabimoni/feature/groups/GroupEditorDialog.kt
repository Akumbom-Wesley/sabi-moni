package com.sabimoni.feature.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.Alignment
import com.sabimoni.core.data.entity.RecurrenceUnit
import com.sabimoni.core.data.model.RecurrenceSchedule
import com.sabimoni.ui.components.DateChoiceRow
import java.time.LocalDate
import com.sabimoni.core.data.model.MoneyGroup
import com.sabimoni.core.money.format
import com.sabimoni.core.money.parseMoney
import com.sabimoni.ui.components.ChipFlowRow

private val LEAD_DAY_CHOICES = listOf(1, 2, 3, 7)

/**
 * Create or edit a group (FR4.1): a name, the penalty for missing a contribution, and
 * how many days ahead to warn.
 *
 * The penalty is optional but load-bearing — it is what the reminder puts in front of you
 * (FR4.4), and the reason this pillar of the app exists. Left empty it simply is not
 * mentioned, rather than being shown as zero.
 */
@Composable
fun GroupEditorDialog(
    group: MoneyGroup?,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (GroupEdit) -> Unit,
) {
    var name by rememberSaveable(group?.id) { mutableStateOf(group?.name.orEmpty()) }
    var penaltyText by rememberSaveable(group?.id) {
        mutableStateOf(group?.penalty?.format(withCurrency = false).orEmpty())
    }
    var leadDays by rememberSaveable(group?.id) {
        mutableStateOf(group?.reminderLeadDays ?: DEFAULT_LEAD_DAYS)
    }
    var recurring by rememberSaveable(group?.id) {
        mutableStateOf(group?.recurrence != null)
    }
    var recurringAmountText by rememberSaveable(group?.id) {
        mutableStateOf(group?.recurrence?.amount?.format(withCurrency = false).orEmpty())
    }
    var unit by rememberSaveable(group?.id) {
        mutableStateOf(group?.recurrence?.unit ?: RecurrenceUnit.MONTHLY)
    }
    var anchorEpochDay by rememberSaveable(group?.id) {
        mutableStateOf((group?.recurrence?.anchor ?: today).toEpochDay())
    }

    val penalty = remember(penaltyText) { parseMoney(penaltyText) }
    val penaltyInvalid = penaltyText.isNotBlank() && penalty == null
    val recurringAmount = remember(recurringAmountText) { parseMoney(recurringAmountText) }
    val anchor = LocalDate.ofEpochDay(anchorEpochDay)

    // A schedule needs all three parts to produce a deadline, so Save waits for the amount.
    val schedule = recurringAmount?.takeIf { recurring }?.let {
        RecurrenceSchedule(unit = unit, amount = it, anchor = anchor)
    }
    val scheduleIncomplete = recurring && schedule == null

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

                HorizontalDivider()

                // FR4.x's standing commitment: set once, and the app produces each
                // period's obligation from it (ADR-0029).
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = recurring, onCheckedChange = { recurring = it })
                    Text(
                        text = "I owe this group regularly",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                if (recurring) {
                    OutlinedTextField(
                        value = recurringAmountText,
                        onValueChange = { recurringAmountText = it },
                        label = { Text("Amount each time (FCFA)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = recurringAmountText.isNotBlank() && recurringAmount == null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text("How often", style = MaterialTheme.typography.labelMedium)
                    ChipFlowRow {
                        RecurrenceUnit.entries.forEach { option ->
                            FilterChip(
                                selected = unit == option,
                                onClick = { unit = option },
                                label = { Text(option.label, maxLines = 1, softWrap = false) },
                            )
                        }
                    }

                    Text("First deadline", style = MaterialTheme.typography.labelMedium)
                    DateChoiceRow(
                        selected = anchor,
                        shortcuts = listOf(
                            "Today" to today,
                            "In a week" to today.plusWeeks(1),
                            "Month end" to today.withDayOfMonth(today.lengthOfMonth()),
                        ),
                        onSelect = { anchorEpochDay = it.toEpochDay() },
                    )
                    Text(
                        text = "Every deadline after this one follows automatically, and " +
                            "each gets its own reminder.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                HorizontalDivider()

                Text("Warn me this far ahead", style = MaterialTheme.typography.labelMedium)
                ChipFlowRow {
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
                            penalty = penalty,
                            reminderLeadDays = leadDays,
                            recurrence = schedule,
                        ),
                    )
                },
                enabled = name.isNotBlank() && !penaltyInvalid && !scheduleIncomplete,
            ) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}


private const val DEFAULT_LEAD_DAYS = 2

/** Wording, not domain — kept next to the chips that show it. */
private val RecurrenceUnit.label: String
    get() = when (this) {
        RecurrenceUnit.WEEKLY -> "Weekly"
        RecurrenceUnit.FORTNIGHTLY -> "Every 2 weeks"
        RecurrenceUnit.MONTHLY -> "Monthly"
        RecurrenceUnit.QUARTERLY -> "Every 3 months"
    }
