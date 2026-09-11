package com.sabimoni.feature.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sabimoni.core.data.entity.Direction
import com.sabimoni.core.data.model.Category
import com.sabimoni.core.data.model.LoggedEntry
import com.sabimoni.core.money.Money
import com.sabimoni.core.money.format
import com.sabimoni.core.money.parseMoney
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * A validated set of values the editor produced. Constructing one means the amount parsed
 * — the screen never hands the repository a half-typed field.
 */
data class EntryEdit(
    val amount: Money,
    val direction: Direction,
    val categoryId: Long?,
    val note: String?,
    val occurredOn: LocalDate,
)

/** Category ids autogenerate from 1, so 0 is free to mean "leave it uncategorised". */
private const val NO_CATEGORY = 0L

/**
 * One editor for both jobs: correcting a line the parser produced (FR1.4) and entering one
 * by hand (FR1.6). They collect identical values, and keeping them as one composable is
 * what makes a manual entry correctable by exactly the same tap as a parsed one.
 *
 * Group is absent on purpose — see docs/adr/0018-corrections-and-manual-entry.md.
 *
 * @param entry the line being corrected, or null to enter a new one.
 * @param onDelete offered only for an existing entry.
 */
@Composable
fun EntryEditorDialog(
    entry: LoggedEntry?,
    today: LocalDate,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (EntryEdit) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    // Pre-filled with the amount already grouped ("12 500", not "12500"): the form opens on
    // what the parser understood, readable, and `parseMoney` reads its own output back.
    var amountText by rememberSaveable(entry?.id) {
        mutableStateOf(entry?.amount?.format(withCurrency = false).orEmpty())
    }
    var direction by rememberSaveable(entry?.id) {
        mutableStateOf(entry?.direction ?: Direction.EXPENSE)
    }
    var categoryId by rememberSaveable(entry?.id) {
        mutableStateOf(entry?.categoryId ?: NO_CATEGORY)
    }
    var note by rememberSaveable(entry?.id) { mutableStateOf(entry?.note.orEmpty()) }
    // Held as an epoch day rather than a LocalDate so it survives process death without a
    // custom Saver.
    var epochDay by rememberSaveable(entry?.id) {
        mutableStateOf((entry?.occurredOn ?: today).toEpochDay())
    }
    var showDatePicker by rememberSaveable(entry?.id) { mutableStateOf(false) }

    val amount = remember(amountText) { parseMoney(amountText) }
    val occurredOn = LocalDate.ofEpochDay(epochDay)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (entry == null) "Add an entry" else "Fix this entry") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (FCFA)") },
                    // No decimal key: XAF has no minor unit (ADR-0006).
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = amountText.isNotEmpty() && amount == null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                ChipRow {
                    FilterChip(
                        selected = direction == Direction.EXPENSE,
                        onClick = { direction = Direction.EXPENSE },
                        label = { Text("Spent") },
                    )
                    FilterChip(
                        selected = direction == Direction.INCOME,
                        onClick = { direction = Direction.INCOME },
                        label = { Text("Received") },
                    )
                }

                CategoryPicker(
                    categories = categories,
                    selectedId = categoryId,
                    onSelect = { categoryId = it },
                )

                // Labels stay short and single-line so the three of them fit one row at
                // dialog width — an ISO date here wrapped the chip (ADR-0019).
                val customDate = occurredOn != today && occurredOn != today.minusDays(1)
                ChipRow {
                    DateChip(
                        label = "Today",
                        selected = occurredOn == today,
                        onClick = { epochDay = today.toEpochDay() },
                    )
                    DateChip(
                        label = "Yesterday",
                        selected = occurredOn == today.minusDays(1),
                        onClick = { epochDay = today.minusDays(1).toEpochDay() },
                    )
                    DateChip(
                        label = if (customDate) occurredOn.format(SHORT_DATE) else "Pick…",
                        selected = customDate,
                        onClick = { showDatePicker = true },
                    )
                }

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
                            text = "Delete this entry",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsed = amount ?: return@TextButton
                    onSave(
                        EntryEdit(
                            amount = parsed,
                            direction = direction,
                            categoryId = categoryId.takeIf { it != NO_CATEGORY },
                            note = note.trim().takeIf(String::isNotEmpty),
                            occurredOn = occurredOn,
                        ),
                    )
                },
                enabled = amount != null,
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )

    if (showDatePicker) {
        DateChooser(
            initial = occurredOn,
            onDismiss = { showDatePicker = false },
            onPick = { picked ->
                epochDay = picked.toEpochDay()
                showDatePicker = false
            },
        )
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

/** `d MMM` — short enough that three date chips sit on one row without wrapping. */
private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

@Composable
private fun DateChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = label, maxLines = 1, softWrap = false) },
    )
}

/**
 * Existing categories plus "Uncategorised", and nothing else. A correction resolves a
 * category by id against a row the user can already see, so no path through this screen
 * adds taxonomy — category management is its own decision, not a side effect of fixing an
 * amount. See docs/adr/0018-corrections-and-manual-entry.md.
 */
@Composable
private fun CategoryPicker(
    categories: List<Category>,
    selectedId: Long,
    onSelect: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = categories.firstOrNull { it.id == selectedId }?.name ?: "Uncategorised"

    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(selectedName)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Uncategorised") },
                onClick = {
                    onSelect(NO_CATEGORY)
                    expanded = false
                },
            )
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    onClick = {
                        onSelect(category.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateChooser(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    // The picker speaks in UTC millis, so both directions use UTC. Anything else shifts
    // the chosen day by one wherever the device offset is not zero.
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli(),
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = state.selectedDateMillis ?: return@TextButton
                    onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                },
                enabled = state.selectedDateMillis != null,
            ) {
                Text("OK")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}
