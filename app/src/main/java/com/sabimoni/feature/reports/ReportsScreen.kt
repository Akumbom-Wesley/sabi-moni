package com.sabimoni.feature.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabimoni.core.data.entity.Direction
import com.sabimoni.core.data.model.LoggedEntry
import com.sabimoni.core.money.Money
import com.sabimoni.core.money.format
import com.sabimoni.core.report.DateRange
import com.sabimoni.core.report.ReportFocus
import com.sabimoni.core.report.ReportPeriod
import com.sabimoni.core.report.ReportSlice
import com.sabimoni.ui.components.ChipFlowRow
import com.sabimoni.ui.components.DateChooserDialog
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DAY_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
private val SHORT_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
private val MONTH_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

@Composable
fun ReportsScreen(viewModel: ReportsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ReportsContent(
        state = state,
        onSelectPeriod = viewModel::selectPeriod,
        onStep = viewModel::step,
        onCustomRange = viewModel::setCustomRange,
        onFocus = viewModel::focusOn,
        onClearFocus = viewModel::clearFocus,
    )
}

@Composable
private fun ReportsContent(
    state: ReportsUiState,
    onSelectPeriod: (ReportPeriod) -> Unit,
    onStep: (Int) -> Unit,
    onCustomRange: (LocalDate, LocalDate) -> Unit,
    onFocus: (ReportFocus) -> Unit,
    onClearFocus: () -> Unit,
) {
    val summary = state.summary

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(text = "Reports", style = MaterialTheme.typography.headlineSmall)
        }

        item {
            PeriodChips(
                selected = state.period,
                onSelect = onSelectPeriod,
                onCustomRange = onCustomRange,
                range = state.range,
            )
        }

        item {
            RangeHeader(
                label = state.range.label(state.period),
                canStep = state.canStep,
                onStep = onStep,
            )
        }

        item { TotalsCard(income = summary.income, expense = summary.expense, net = summary.net) }

        if (summary.isEmpty) {
            item {
                Text(
                    text = "Nothing logged in this period.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@LazyColumn
        }

        if (summary.byCategory.isNotEmpty()) {
            item {
                Breakdown(
                    title = "Where it went",
                    slices = summary.byCategory,
                    total = summary.expense,
                    isSelected = { slice ->
                        (state.focus as? ReportFocus.Category)?.id == slice.id
                    },
                    onSlice = { slice ->
                        onFocus(ReportFocus.Category(slice.id, slice.label))
                    },
                )
            }
        }

        if (summary.byGroup.isNotEmpty()) {
            item {
                Breakdown(
                    title = "Groups",
                    slices = summary.byGroup,
                    total = summary.byGroup.fold(Money.ZERO) { acc, s -> acc + s.amount },
                    isSelected = { slice ->
                        (state.focus as? ReportFocus.Group)?.id == slice.id
                    },
                    onSlice = { slice ->
                        slice.id?.let { onFocus(ReportFocus.Group(it, slice.label)) }
                    },
                )
            }
        }

        item {
            EntriesHeader(
                focus = state.focus,
                count = state.visibleEntries.size,
                onClearFocus = onClearFocus,
            )
        }

        items(items = state.visibleEntries, key = LoggedEntry::id) { entry ->
            EntryRow(entry)
        }
    }
}

@Composable
private fun PeriodChips(
    selected: ReportPeriod,
    range: DateRange,
    onSelect: (ReportPeriod) -> Unit,
    onCustomRange: (LocalDate, LocalDate) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingFrom by rememberSaveable { mutableStateOf<Long?>(null) }

    ChipFlowRow {
        ReportPeriod.entries.forEach { period ->
            FilterChip(
                selected = selected == period,
                onClick = {
                    if (period == ReportPeriod.CUSTOM) picking = "from" else onSelect(period)
                },
                label = { Text(period.label, maxLines = 1, softWrap = false) },
            )
        }
    }

    // Two sequential single-date pickers rather than a range picker: the same component
    // the editor and the contribution dialog already use, so the date mechanics exist in
    // exactly one place.
    when (picking) {
        "from" -> DateChooserDialog(
            initial = range.from,
            onDismiss = { picking = null },
            onPick = { from ->
                pendingFrom = from.toEpochDay()
                picking = "to"
            },
        )

        "to" -> DateChooserDialog(
            initial = range.to,
            onDismiss = { picking = null; pendingFrom = null },
            onPick = { to ->
                val from = pendingFrom?.let(LocalDate::ofEpochDay) ?: to
                onCustomRange(from, to)
                picking = null
                pendingFrom = null
            },
        )
    }
}

@Composable
private fun RangeHeader(label: String, canStep: Boolean, onStep: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onStep(-1) }, enabled = canStep) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous period")
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        IconButton(onClick = { onStep(1) }, enabled = canStep) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next period")
        }
    }
}

@Composable
private fun TotalsCard(income: Money, expense: Money, net: Money) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Total("In", income)
            Total("Out", expense)
            Total("Net", net, emphasised = true)
        }
    }
}

@Composable
private fun Total(label: String, amount: Money, emphasised: Boolean = false) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = amount.format(),
            style = if (emphasised) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyLarge
            },
            fontWeight = if (emphasised) FontWeight.SemiBold else FontWeight.Normal,
            color = if (emphasised && amount.isNegative) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/**
 * A ranked bar per slice (FR5.3). Bars rather than a pie: the question is "what cost the
 * most", which is a comparison of lengths against a shared baseline, and a pie answers
 * that worse the more slices it has.
 */
@Composable
private fun Breakdown(
    title: String,
    slices: List<ReportSlice>,
    total: Money,
    isSelected: (ReportSlice) -> Boolean,
    onSlice: (ReportSlice) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        slices.forEach { slice ->
            SliceBar(
                slice = slice,
                share = slice.shareOf(total),
                selected = isSelected(slice),
                onClick = { onSlice(slice) },
            )
        }
    }
}

@Composable
private fun SliceBar(
    slice: ReportSlice,
    share: Float,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val barColour = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = slice.label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = slice.amount.format(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(share.coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColour),
            )
        }
    }
}

@Composable
private fun EntriesHeader(focus: ReportFocus, count: Int, onClearFocus: () -> Unit) {
    val label = when (focus) {
        is ReportFocus.All -> "All entries"
        is ReportFocus.Category -> focus.label
        is ReportFocus.Group -> focus.label
    }

    Column {
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$label · $count",
                style = MaterialTheme.typography.titleMedium,
            )
            if (focus !is ReportFocus.All) {
                TextButton(onClick = onClearFocus) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Text(" Show all")
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: LoggedEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.note?.takeIf(String::isNotBlank)
                        ?: entry.categoryName
                        ?: "Uncategorised",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = buildString {
                        append(entry.occurredOn.format(SHORT_DAY))
                        entry.groupName?.let { append(" · ").append(it) }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = entry.amount.format(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (entry.direction == Direction.INCOME) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

private val ReportPeriod.label: String
    get() = when (this) {
        ReportPeriod.DAY -> "Day"
        ReportPeriod.WEEK -> "Week"
        ReportPeriod.MONTH -> "Month"
        ReportPeriod.QUARTER -> "Quarter"
        ReportPeriod.YEAR -> "Year"
        ReportPeriod.CUSTOM -> "Custom"
    }

/** Names the span the way a person would say it, rather than printing two ISO dates. */
private fun DateRange.label(period: ReportPeriod): String = when (period) {
    ReportPeriod.DAY -> from.format(DAY_LABEL)
    ReportPeriod.MONTH -> from.format(MONTH_LABEL)
    ReportPeriod.QUARTER -> "Q${(from.monthValue - 1) / 3 + 1} ${from.year}"
    ReportPeriod.YEAR -> from.year.toString()
    ReportPeriod.WEEK, ReportPeriod.CUSTOM ->
        if (isSingleDay) from.format(DAY_LABEL) else "${from.format(SHORT_DAY)} – ${to.format(DAY_LABEL)}"
}
