package com.sabimoni.feature.capture

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabimoni.core.data.entity.Direction
import com.sabimoni.core.data.entity.MessageSource
import com.sabimoni.core.data.entity.ParseStatus
import com.sabimoni.core.data.model.CapturedMessage
import com.sabimoni.core.data.model.DayTotals
import com.sabimoni.core.data.model.LoggedEntry
import com.sabimoni.core.data.model.ThreadItem
import com.sabimoni.core.money.Money
import com.sabimoni.core.money.format
import java.time.LocalDate
import java.time.ZoneId

/** What you typed, right-aligned: squared off on the side it points from. */
private val SentBubble = RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp)

/** What the app made of it, left-aligned. */
private val ReplyCard = RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)

@Composable
fun CaptureScreen(viewModel: CaptureViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CaptureContent(
        state = state,
        onSend = viewModel::send,
        onRetry = viewModel::retry,
        onAdd = viewModel::addEntry,
        onCorrect = viewModel::correctEntry,
        onDeleteEntries = viewModel::deleteEntries,
    )
}

/** Which entry the editor is open on, if any. */
private sealed interface EditorRequest {
    /** Form entry from scratch (FR1.6). */
    data object New : EditorRequest

    /** Correcting a line already logged (FR1.4). */
    data class Correct(val entry: LoggedEntry) : EditorRequest
}

@Composable
private fun CaptureContent(
    state: CaptureUiState,
    onSend: (String) -> Unit,
    onRetry: (Long) -> Unit,
    onAdd: (EntryEdit) -> Unit,
    onCorrect: (Long, EntryEdit) -> Unit,
    onDeleteEntries: (Set<Long>) -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var editor by remember { mutableStateOf<EditorRequest?>(null) }
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    var confirmDelete by remember { mutableStateOf(false) }

    val zone = remember { ZoneId.systemDefault() }
    val rows = remember(state.items, state.today, zone) {
        state.items.withDayHeaders(zone, skip = state.today)
    }
    val presentIds = remember(state.items) { state.items.entryIds() }

    // An entry can vanish under a selection — deleted here, or re-parsed elsewhere — and a
    // count that includes rows that no longer exist is a lie about what Delete will do.
    LaunchedEffect(presentIds) {
        if (!presentIds.containsAll(selected)) {
            selected = selected intersect presentIds
        }
    }

    val selecting = selected.isNotEmpty()

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        if (selecting) {
            SelectionBar(
                count = selected.size,
                onClear = { selected = emptySet() },
                onDelete = { confirmDelete = true },
            )
        } else {
            BalanceSummary(
                balance = state.balance,
                totals = state.totals,
                pendingCount = state.pendingCount,
            )
        }

        // The thread sits on its own surface so the header and the composer read as
        // chrome and the conversation reads as content.
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(items = rows, key = ThreadRow::key) { row ->
                when (row) {
                    is ThreadRow.DayHeader -> DayHeader(row.day, state.today)
                    is ThreadRow.Item -> {
                        val toggleOne: (Long) -> Unit = { id ->
                            selected = if (id in selected) selected - id else selected + id
                        }
                        // Long-pressing a card selects everything on it: a bad parse is
                        // usually wrong as a whole, and the card is the unit you pressed.
                        val toggleAll: (List<Long>) -> Unit = { ids ->
                            selected = when {
                                ids.isEmpty() -> selected
                                selected.containsAll(ids) -> selected - ids.toSet()
                                else -> selected + ids
                            }
                        }
                        when (val item = row.item) {
                            is ThreadItem.Captured -> MessageExchange(
                                message = item.message,
                                selecting = selecting,
                                selected = selected,
                                onToggleEntry = toggleOne,
                                onToggleCard = {
                                    toggleAll(item.message.lineItems.map(LoggedEntry::id))
                                },
                                onEdit = { editor = EditorRequest.Correct(it) },
                                onRetry = { onRetry(item.message.id) },
                            )
                            is ThreadItem.Manual -> ManualEntryCard(
                                entry = item.entry,
                                selecting = selecting,
                                selected = item.entry.id in selected,
                                onToggle = { toggleOne(item.entry.id) },
                                onEdit = { editor = EditorRequest.Correct(item.entry) },
                            )
                        }
                    }
                }
            }
        }

        Composer(
            draft = draft,
            onDraftChange = { draft = it },
            onSend = {
                onSend(draft)
                draft = ""
            },
            onAddByHand = { editor = EditorRequest.New },
        )
    }

    when (val request = editor) {
        null -> Unit
        EditorRequest.New -> EntryEditorDialog(
            entry = null,
            today = state.today,
            categories = state.categories,
            onDismiss = { editor = null },
            onSave = { edit ->
                onAdd(edit)
                editor = null
            },
        )
        is EditorRequest.Correct -> EntryEditorDialog(
            entry = request.entry,
            today = state.today,
            categories = state.categories,
            onDismiss = { editor = null },
            onSave = { edit ->
                onCorrect(request.entry.id, edit)
                editor = null
            },
            onDelete = {
                onDeleteEntries(setOf(request.entry.id))
                editor = null
            },
        )
    }

    if (confirmDelete) {
        DeleteConfirmation(
            count = selected.size,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                onDeleteEntries(selected)
                selected = emptySet()
                confirmDelete = false
            },
        )
    }
}

// --- header -------------------------------------------------------------------------

/**
 * Balance as the headline, today's figures beneath it (FR1.5, ADR-0022).
 *
 * The balance answers "how am I doing" and the day answers "what have I logged tonight" —
 * two different questions, so they get two different weights rather than competing as
 * equals. The caption is there because this figure is only as true as what has been
 * entered, and calling it "Balance" unqualified would overstate what the app knows.
 */
@Composable
private fun BalanceSummary(balance: Money, totals: DayTotals, pendingCount: Int) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "Balance",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = balance.format(),
            style = MaterialTheme.typography.headlineMedium,
            color = if (balance.isNegative) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
        Text(
            text = "from everything logged so far",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Today", style = MaterialTheme.typography.titleMedium)
            Text(
                text = totals.net.signed(),
                style = MaterialTheme.typography.titleMedium,
                color = if (totals.net.isNegative) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
        Text(
            text = "${totals.income.format()} in · ${totals.expense.format()} out",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (pendingCount > 0) {
            Text(
                text = "$pendingCount waiting to be interpreted",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Takes the summary's place while entries are selected, so the header does not change
 * height and the thread underneath does not jump as the selection grows.
 */
@Composable
private fun SelectionBar(count: Int, onClear: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClear) {
            Icon(Icons.Default.Close, contentDescription = "Clear selection")
        }
        Text(
            text = "$count selected",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete selected entries",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

// --- thread -------------------------------------------------------------------------

/** A centred marker rather than a heading: it marks a position in a list you scroll. */
@Composable
private fun DayHeader(day: LocalDate, today: LocalDate) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Text(
                text = dayLabel(day, today),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}

/**
 * One exchange: what was sent, then what the app made of it.
 *
 * Two shapes rather than one block, because they are two different kinds of thing — a
 * sentence the user wrote, and a set of entries the app derived. The old single tinted
 * card gave them equal weight and made a screen of parsed messages a wall of colour.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageExchange(
    message: CapturedMessage,
    selecting: Boolean,
    selected: Set<Long>,
    onToggleEntry: (Long) -> Unit,
    onToggleCard: () -> Unit,
    onEdit: (LoggedEntry) -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // On the whole exchange, so a long press anywhere on it starts a selection —
            // the sent bubble and the status line included, not just the entry rows.
            .combinedClickable(
                onClick = { if (selecting) onToggleCard() },
                onLongClick = onToggleCard,
            ),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                shape = SentBubble,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.widthIn(max = 300.dp),
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (message.source == MessageSource.SMS) {
                        Text(
                            text = "MoMo alert",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                    Text(
                        text = message.rawText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        val failed = message.status == ParseStatus.FAILED
        Surface(
            shape = ReplyCard,
            color = if (failed) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = if (failed) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(
                    start = 14.dp,
                    end = 6.dp,
                    top = 10.dp,
                    bottom = if (message.lineItems.isEmpty()) 10.dp else 4.dp,
                ),
            ) {
                Text(
                    text = statusLabel(message),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (failed) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )

                message.lineItems.forEachIndexed { index, entry ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(end = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    EntryRow(
                        entry = entry,
                        selecting = selecting,
                        selected = entry.id in selected,
                        onToggle = { onToggleEntry(entry.id) },
                        onLongPress = onToggleCard,
                        onEdit = { onEdit(entry) },
                    )
                }

                if (message.needsRetryOffer) {
                    TextButton(onClick = onRetry) {
                        Text(if (failed) "Try again" else "Try now")
                    }
                }
            }
        }
    }
}

/** An entry typed straight into the form — it has no message to sit under. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ManualEntryCard(
    entry: LoggedEntry,
    selecting: Boolean,
    selected: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
) {
    Surface(
        shape = ReplyCard,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (selecting) onToggle() },
                onLongClick = onToggle,
            ),
    ) {
        Column(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 4.dp),
        ) {
            Text(
                text = "entered by hand",
                style = MaterialTheme.typography.labelSmall,
            )
            EntryRow(
                entry = entry,
                selecting = selecting,
                selected = selected,
                onToggle = onToggle,
                onLongPress = onToggle,
                onEdit = onEdit,
            )
        }
    }
}

/**
 * One thing that was logged — the confirmation of FR1.3, and the thing FR1.4 corrects.
 *
 * Category leads and the note sits under it, with the amount right-aligned in its own
 * column, so a run of entries lines up as a readable ledger instead of a row of
 * run-together text. Direction is carried by the sign and the colour, not by a word.
 *
 * Editing is an explicit button, not a tap on the line: the row is a *statement of record*
 * and a mis-tap on it should cost nothing. A long press does the same as a long press
 * anywhere else on the card — it selects the card — so the gesture has one meaning
 * wherever it lands. See ADR-0019.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(
    entry: LoggedEntry,
    selecting: Boolean,
    selected: Boolean,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
    onEdit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                // Outside a selection a tap on the row does nothing at all, on purpose.
                onClick = { if (selecting) onToggle() },
                onLongClick = onLongPress,
            )
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            Checkbox(checked = selected, onCheckedChange = { onToggle() })
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.categoryName ?: "Uncategorised",
                style = MaterialTheme.typography.bodyMedium,
            )
            entry.note?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            text = entry.signedAmount(),
            style = MaterialTheme.typography.titleSmall,
            color = if (entry.direction == Direction.INCOME) {
                MaterialTheme.colorScheme.primary
            } else {
                Color.Unspecified
            },
        )

        if (selecting) {
            // Keeps the amount column in the same place whether or not the pencil is
            // showing, so switching into a selection does not reflow every row.
            Spacer(modifier = Modifier.size(EDIT_BUTTON_SIZE))
        } else {
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Fix this entry",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** The touch target `IconButton` reserves, mirrored by the spacer that replaces it. */
private val EDIT_BUTTON_SIZE = 48.dp

// --- composer -----------------------------------------------------------------------

@Composable
private fun Composer(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onAddByHand: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedIconButton(onClick = onAddByHand) {
            Icon(Icons.Default.Add, contentDescription = "Add an entry by hand")
        }
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("taxi 500, lunch 1500, gave 5000 to choir") },
            shape = RoundedCornerShape(22.dp),
            maxLines = 5,
        )
        FilledIconButton(onClick = onSend, enabled = draft.isNotBlank()) {
            Icon(Icons.Default.Send, contentDescription = "Send")
        }
    }
}

@Composable
private fun DeleteConfirmation(count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (count == 1) "Delete this entry?" else "Delete $count entries?")
        },
        text = {
            Text(
                "The messages stay in the thread, and today's total will be recalculated. " +
                    "This cannot be undone.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// --- thread assembly ----------------------------------------------------------------

/** A position in the rendered thread: either a day separator or one logged thing. */
private sealed interface ThreadRow {
    val key: String

    data class DayHeader(val day: LocalDate) : ThreadRow {
        override val key: String get() = "day-$day"
    }

    data class Item(val item: ThreadItem) : ThreadRow {
        override val key: String get() = item.key
    }
}

/**
 * Inserts a separator wherever the day changes, which is what makes an earlier day legible
 * in a thread that holds every day (FR1.5).
 *
 * Grouped by when a thing was logged, not the day it happened: this is a chat thread, and
 * an entry backdated to last Tuesday still arrived tonight. The date it happened on is on
 * the line itself, and in the totals.
 *
 * @param skip a day to leave unlabelled — today, whose heading the summary above already
 * carries. Two "Today" headings on one screen said the same thing twice.
 */
private fun List<ThreadItem>.withDayHeaders(zone: ZoneId, skip: LocalDate?): List<ThreadRow> {
    val rows = mutableListOf<ThreadRow>()
    var currentDay: LocalDate? = null
    forEach { item ->
        val day = item.at.atZone(zone).toLocalDate()
        if (day != currentDay) {
            if (day != skip) rows += ThreadRow.DayHeader(day)
            currentDay = day
        }
        rows += ThreadRow.Item(item)
    }
    return rows
}

/** Every entry id currently in the thread, whatever kind of item it hangs off. */
private fun List<ThreadItem>.entryIds(): Set<Long> = flatMapTo(mutableSetOf()) { item ->
    when (item) {
        is ThreadItem.Captured -> item.message.lineItems.map(LoggedEntry::id)
        is ThreadItem.Manual -> listOf(item.entry.id)
    }
}

// --- labels -------------------------------------------------------------------------

private fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> day.toString()
}

private fun LoggedEntry.signedAmount(): String {
    val sign = if (direction == Direction.EXPENSE) "-" else "+"
    return sign + amount.format()
}

/** [Money.format] already carries the minus, so only a surplus needs a sign added. */
private fun Money.signed(): String = if (xaf > 0L) "+" + format() else format()

/**
 * True once something has actually gone wrong. A `failureReason` on a still-pending
 * message means an attempt failed and another is queued behind a backoff delay.
 */
private val CapturedMessage.needsRetryOffer: Boolean
    get() = status == ParseStatus.FAILED ||
        (status == ParseStatus.PENDING_PARSE && failureReason != null)

private fun statusLabel(message: CapturedMessage): String = when (message.status) {
    // Saying *why* it is still waiting, rather than only that it is: a silent retry
    // behind an invisible timer looks exactly like one about to succeed (ADR-0021).
    ParseStatus.PENDING_PARSE -> message.failureReason
        ?.let { "still trying — $it" }
        ?: "waiting to be interpreted"
    ParseStatus.PARSED -> when (val count = message.lineItems.size) {
        0 -> "nothing to log here"
        1 -> "understood 1 entry"
        else -> "understood $count entries"
    }
    ParseStatus.FAILED -> message.failureReason ?: "could not be interpreted"
}
