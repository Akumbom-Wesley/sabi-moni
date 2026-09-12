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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import com.sabimoni.core.data.entity.Direction
import com.sabimoni.core.data.model.Category
import com.sabimoni.core.data.model.LoggedEntry
import com.sabimoni.core.data.model.MoneyGroup
import com.sabimoni.core.money.Money
import com.sabimoni.core.money.format
import com.sabimoni.core.money.parseMoney
import com.sabimoni.ui.components.DateChoiceRow
import java.time.LocalDate

/**
 * A validated set of values the editor produced. Constructing one means the amount parsed
 * — the screen never hands the repository a half-typed field.
 */
data class EntryEdit(
    val amount: Money,
    val direction: Direction,
    val categoryId: Long?,
    val groupId: Long?,
    val note: String?,
    val occurredOn: LocalDate,
)

/** Row ids autogenerate from 1, so 0 is free to mean "none chosen". */
private const val NOTHING_CHOSEN = 0L

/** One choosable row, so the category and group pickers are the same component. */
private data class PickerOption(val id: Long, val name: String)

/**
 * One editor for both jobs: correcting a line the parser produced (FR1.4) and entering one
 * by hand (FR1.6). They collect identical values, and keeping them as one composable is
 * what makes a manual entry correctable by exactly the same tap as a parsed one.
 *
 * The group field completes FR1.4. It was absent through Sprint 2 because the schema could
 * not express "this money went to a group" without inventing an obligation — see
 * docs/adr/0025-how-a-transaction-references-a-group.md.
 *
 * @param entry the line being corrected, or null to enter a new one.
 * @param onDelete offered only for an existing entry.
 */
@Composable
fun EntryEditorDialog(
    entry: LoggedEntry?,
    today: LocalDate,
    categories: List<Category>,
    groups: List<MoneyGroup>,
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
        mutableStateOf(entry?.categoryId ?: NOTHING_CHOSEN)
    }
    var groupId by rememberSaveable(entry?.id) {
        mutableStateOf(entry?.groupId ?: NOTHING_CHOSEN)
    }
    var note by rememberSaveable(entry?.id) { mutableStateOf(entry?.note.orEmpty()) }
    // Held as an epoch day rather than a LocalDate so it survives process death without a
    // custom Saver.
    var epochDay by rememberSaveable(entry?.id) {
        mutableStateOf((entry?.occurredOn ?: today).toEpochDay())
    }

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

                OptionPicker(
                    options = categories.map { PickerOption(it.id, it.name) },
                    selectedId = categoryId,
                    noneLabel = "Uncategorised",
                    onSelect = { categoryId = it },
                )

                // FR1.4's group field, unblocked by ADR-0025.
                val groupFixed = entry?.isGroupFixed == true
                OptionPicker(
                    options = groups.map { PickerOption(it.id, it.name) },
                    selectedId = groupId,
                    noneLabel = "No group",
                    enabled = !groupFixed,
                    onSelect = { groupId = it },
                )
                if (groupFixed) {
                    Text(
                        text = "Set by the contribution this pays. Change it on the group.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                DateChoiceRow(
                    selected = occurredOn,
                    shortcuts = listOf("Today" to today, "Yesterday" to today.minusDays(1)),
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
                            categoryId = categoryId.takeIf { it != NOTHING_CHOSEN },
                            groupId = groupId.takeIf { it != NOTHING_CHOSEN },
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

}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}


/**
 * Existing rows plus a "none" option, and nothing else. A correction resolves by id
 * against something the user can already see, so no path through this screen invents a
 * category or a group — managing those is its own decision, not a side effect of fixing an
 * amount at 11pm. See ADR-0018 and ADR-0025.
 *
 * One component for both pickers: they differ only in wording, and a second copy would be
 * a second place for the "never create" rule to be forgotten.
 *
 * @param enabled false for a group that is fixed by the obligation being paid, where the
 * disabled control plus its caption says more than hiding the field would.
 */
@Composable
private fun OptionPicker(
    options: List<PickerOption>,
    selectedId: Long,
    noneLabel: String,
    enabled: Boolean = true,
    onSelect: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = options.firstOrNull { it.id == selectedId }?.name ?: noneLabel

    Box {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = selectedName, maxLines = 1)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(noneLabel) },
                onClick = {
                    onSelect(NOTHING_CHOSEN)
                    expanded = false
                },
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name) },
                    onClick = {
                        onSelect(option.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

