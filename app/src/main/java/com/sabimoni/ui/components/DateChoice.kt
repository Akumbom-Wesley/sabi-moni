package com.sabimoni.ui.components

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** `d MMM` rather than an ISO date: short enough not to blow out a chip (ADR-0019). */
private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

/**
 * A row of quick date choices plus a full picker.
 *
 * Shared between the entry editor, whose shortcuts look backwards ("Yesterday"), and the
 * contribution dialog, whose due dates look forwards ("In a week") — so the caller
 * supplies the shortcuts and only the picker mechanics live here.
 *
 * @param shortcuts label to date, in the order they should appear. Labels stay short so
 * a chip holds one line; the row itself wraps when they do not all fit.
 */
@Composable
fun DateChoiceRow(
    selected: LocalDate,
    shortcuts: List<Pair<String, LocalDate>>,
    onSelect: (LocalDate) -> Unit,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val isCustom = shortcuts.none { it.second == selected }

    // Wraps rather than clips: the contribution dialog's shortcuts ("In a week") are wide
    // enough that four chips do not fit one line at dialog width.
    ChipFlowRow {
        shortcuts.forEach { (label, date) ->
            DateChip(
                label = label,
                selected = selected == date,
                onClick = { onSelect(date) },
            )
        }
        DateChip(
            label = if (isCustom) selected.format(SHORT_DATE) else "Pick…",
            selected = isCustom,
            onClick = { showPicker = true },
        )
    }

    if (showPicker) {
        DateChooserDialog(
            initial = selected,
            onDismiss = { showPicker = false },
            onPick = { picked ->
                onSelect(picked)
                showPicker = false
            },
        )
    }
}

@Composable
private fun DateChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = label, maxLines = 1, softWrap = false) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateChooserDialog(
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
