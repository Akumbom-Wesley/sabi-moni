package com.sabimoni.feature.groups

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabimoni.core.data.entity.ContributionStatus
import com.sabimoni.core.data.model.Contribution
import com.sabimoni.core.data.model.GroupSummary
import com.sabimoni.core.data.model.MoneyGroup
import com.sabimoni.core.money.Money
import com.sabimoni.core.money.format
import java.time.LocalDate

@Composable
fun GroupsScreen(viewModel: GroupsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GroupsContent(
        state = state,
        onSaveGroup = viewModel::saveGroup,
        onSaveContribution = viewModel::saveContribution,
        onMarkPaid = viewModel::markPaid,
        onMarkMissed = viewModel::markMissed,
        onReopen = viewModel::reopen,
        onDeleteContribution = viewModel::deleteContribution,
    )
}

private sealed interface GroupsDialog {
    data class EditGroup(val group: MoneyGroup?) : GroupsDialog
    data class EditContribution(val contribution: Contribution?) : GroupsDialog
}

@Composable
private fun GroupsContent(
    state: GroupsUiState,
    onSaveGroup: (GroupEdit) -> Unit,
    onSaveContribution: (ContributionEdit) -> Unit,
    onMarkPaid: (Long) -> Unit,
    onMarkMissed: (Long) -> Unit,
    onReopen: (Long) -> Unit,
    onDeleteContribution: (Long) -> Unit,
) {
    var dialog by remember { mutableStateOf<GroupsDialog?>(null) }
    var expandedGroupId by remember { mutableStateOf<Long?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        OwedHeader(total = state.totalOutstanding, dueCount = state.outstanding.size)
        HorizontalDivider()

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Only asked for once there is something to be reminded about — a permission
            // prompt on an empty screen is a prompt with no reason attached.
            if (state.outstanding.isNotEmpty()) {
                item(key = "notification-permission") { NotificationPermissionPrompt() }
            }

            if (state.outstanding.isNotEmpty()) {
                item(key = "owed-header") { SectionLabel("Due now") }
                items(items = state.outstanding, key = { "owed-${it.id}" }) { contribution ->
                    ContributionCard(
                        contribution = contribution,
                        today = state.today,
                        onMarkPaid = { onMarkPaid(contribution.id) },
                        onMarkMissed = { onMarkMissed(contribution.id) },
                        onEdit = { dialog = GroupsDialog.EditContribution(contribution) },
                    )
                }
            }

            item(key = "groups-header") { SectionLabel("Groups") }

            if (state.summaries.isEmpty()) {
                item(key = "groups-empty") {
                    Text(
                        text = "No groups yet. Add the ones that ask you for money — " +
                            "church, choir, charity, school.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(items = state.summaries, key = { "group-${it.group.id}" }) { summary ->
                GroupCard(
                    summary = summary,
                    today = state.today,
                    expanded = expandedGroupId == summary.group.id,
                    history = state.historyByGroup[summary.group.id].orEmpty(),
                    onToggleExpanded = {
                        expandedGroupId =
                            if (expandedGroupId == summary.group.id) null else summary.group.id
                    },
                    onEdit = { dialog = GroupsDialog.EditGroup(summary.group) },
                    onEditContribution = { dialog = GroupsDialog.EditContribution(it) },
                    onMarkPaid = onMarkPaid,
                    onMarkMissed = onMarkMissed,
                    onReopen = onReopen,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { dialog = GroupsDialog.EditGroup(null) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(" Group")
            }
            Button(
                onClick = { dialog = GroupsDialog.EditContribution(null) },
                enabled = state.summaries.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(" Contribution")
            }
        }
    }

    when (val open = dialog) {
        null -> Unit
        is GroupsDialog.EditGroup -> GroupEditorDialog(
            group = open.group,
            onDismiss = { dialog = null },
            onSave = { edit ->
                onSaveGroup(edit)
                dialog = null
            },
        )
        is GroupsDialog.EditContribution -> ContributionEditorDialog(
            contribution = open.contribution,
            groups = state.groups,
            today = state.today,
            onDismiss = { dialog = null },
            onSave = { edit ->
                onSaveContribution(edit)
                dialog = null
            },
            onDelete = open.contribution?.let { existing ->
                {
                    onDeleteContribution(existing.id)
                    dialog = null
                }
            },
        )
    }
}

/** FR4.6 — what I owe right now, across all groups, as the headline. */
@Composable
private fun OwedHeader(total: Money, dueCount: Int) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "Owed right now",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = total.format(),
            style = MaterialTheme.typography.headlineMedium,
            color = if (total.isZero) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        Text(
            text = when (dueCount) {
                0 -> "Nothing outstanding"
                1 -> "1 contribution waiting"
                else -> "$dueCount contributions waiting"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/**
 * Android 13+ silently drops notifications without this permission, so a reminder that
 * was never shown would look identical to a reminder that never fired. Asked for here,
 * next to the obligations it is about.
 */
@Composable
private fun NotificationPermissionPrompt() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { allowed -> granted = allowed }

    if (granted) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Reminders are off",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "Without notification permission the app cannot warn you before a " +
                    "contribution is due.",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                Text("Turn on reminders")
            }
        }
    }
}

/** One outstanding obligation, with the penalty stated and the two ways to settle it. */
@Composable
private fun ContributionCard(
    contribution: Contribution,
    today: LocalDate,
    onMarkPaid: () -> Unit,
    onMarkMissed: () -> Unit,
    onEdit: () -> Unit,
) {
    val overdue = contribution.isOverdue(today)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (overdue) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
    ) {
        Column(modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = contribution.groupName,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = dueLabel(contribution, today),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    text = contribution.amount.format(),
                    style = MaterialTheme.typography.titleSmall,
                )
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit this contribution",
                        modifier = Modifier.padding(2.dp),
                    )
                }
            }

            contribution.penalty?.takeIf { !it.isZero }?.let { penalty ->
                Text(
                    text = "Missing it costs ${penalty.format()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onMarkPaid) { Text("Paid") }
                TextButton(onClick = onMarkMissed) { Text("Missed") }
            }
        }
    }
}

/**
 * A group, what it owes, and its full history when expanded (FR4.5).
 *
 * History lives inside the card rather than on a pushed screen: it is a short list for a
 * single user, and keeping it here means no navigation state to restore.
 */
@Composable
private fun GroupCard(
    summary: GroupSummary,
    today: LocalDate,
    expanded: Boolean,
    history: List<Contribution>,
    onToggleExpanded: () -> Unit,
    onEdit: () -> Unit,
    onEditContribution: (Contribution) -> Unit,
    onMarkPaid: (Long) -> Unit,
    onMarkMissed: (Long) -> Unit,
    onReopen: (Long) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleExpanded),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = summary.group.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = summaryLine(summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit this group")
                }
            }

            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 0.dp))
                if (history.isEmpty()) {
                    Text(
                        text = "Nothing logged for this group yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                history.forEach { contribution ->
                    HistoryRow(
                        contribution = contribution,
                        today = today,
                        onEdit = { onEditContribution(contribution) },
                        onMarkPaid = { onMarkPaid(contribution.id) },
                        onMarkMissed = { onMarkMissed(contribution.id) },
                        onReopen = { onReopen(contribution.id) },
                    )
                }
            } else if (history.isNotEmpty()) {
                Text(
                    text = "Tap to see all ${history.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** One line of history: what it was, when, and whether it was on time (FR4.5). */
@Composable
private fun HistoryRow(
    contribution: Contribution,
    today: LocalDate,
    onEdit: () -> Unit,
    onMarkPaid: () -> Unit,
    onMarkMissed: () -> Unit,
    onReopen: () -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contribution.amount.format(),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = historyLabel(contribution, today),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColour(contribution),
                )
                contribution.note?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            StatusChip(contribution)
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit this contribution")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (contribution.isOutstanding) {
                TextButton(onClick = onMarkPaid) { Text("Paid") }
                TextButton(onClick = onMarkMissed) { Text("Missed") }
            } else {
                TextButton(onClick = onReopen) { Text("Reopen") }
            }
        }
    }
}

@Composable
private fun StatusChip(contribution: Contribution) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = when (contribution.status) {
            ContributionStatus.PAID -> MaterialTheme.colorScheme.primaryContainer
            ContributionStatus.MISSED -> MaterialTheme.colorScheme.errorContainer
            ContributionStatus.PENDING -> MaterialTheme.colorScheme.surfaceContainerHighest
        },
    ) {
        Text(
            text = when (contribution.status) {
                ContributionStatus.PAID -> if (contribution.wasLate) "Paid late" else "Paid"
                ContributionStatus.MISSED -> "Missed"
                ContributionStatus.PENDING -> "Due"
            },
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

// --- labels -------------------------------------------------------------------------

@Composable
private fun statusColour(contribution: Contribution) = when (contribution.status) {
    ContributionStatus.MISSED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun summaryLine(summary: GroupSummary): String {
    if (summary.dueCount == 0) return "Nothing owed"
    val next = summary.nextDueDate?.let { " · next $it" }.orEmpty()
    return "Owed ${summary.outstanding.format()}$next"
}

private fun dueLabel(contribution: Contribution, today: LocalDate): String =
    when (val days = contribution.daysUntilDue(today)) {
        0L -> "Due today"
        1L -> "Due tomorrow"
        in Long.MIN_VALUE..-1L -> "Overdue by ${-days} ${plural(-days, "day")}"
        else -> "Due in $days days · ${contribution.dueDate}"
    }

private fun historyLabel(contribution: Contribution, today: LocalDate): String =
    when (contribution.status) {
        ContributionStatus.PENDING -> dueLabel(contribution, today)
        ContributionStatus.PAID -> {
            val paid = contribution.paidDate
            if (paid == null) {
                "Paid · was due ${contribution.dueDate}"
            } else {
                "Paid $paid · was due ${contribution.dueDate}"
            }
        }
        ContributionStatus.MISSED -> "Missed · was due ${contribution.dueDate}"
    }

private fun plural(count: Long, word: String) = if (count == 1L) word else "${word}s"
