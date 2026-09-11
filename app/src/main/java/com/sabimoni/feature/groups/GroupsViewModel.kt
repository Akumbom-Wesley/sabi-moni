package com.sabimoni.feature.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabimoni.core.data.entity.GroupType
import com.sabimoni.core.data.model.Contribution
import com.sabimoni.core.data.model.GroupSummary
import com.sabimoni.core.data.model.MoneyGroup
import com.sabimoni.core.data.repository.GroupRepository
import com.sabimoni.core.money.Money
import com.sabimoni.core.money.sum
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class GroupsUiState(
    /** Resolved per subscription, like the capture screen's — see [GroupsViewModel]. */
    val today: LocalDate,
    val summaries: List<GroupSummary> = emptyList(),
    /** What I owe right now, across all groups, soonest first — FR4.6. */
    val outstanding: List<Contribution> = emptyList(),
    val totalOutstanding: Money = Money.ZERO,
    /** Full history per group, for the expandable cards — FR4.5. */
    val historyByGroup: Map<Long, List<Contribution>> = emptyMap(),
) {
    val groups: List<MoneyGroup> get() = summaries.map(GroupSummary::group)
}

@HiltViewModel
class GroupsViewModel @Inject constructor(
    private val repository: GroupRepository,
    private val clock: Clock,
) : ViewModel() {

    /**
     * Every view on this screen is derived from one read of the contributions plus one of
     * the groups. Separate queries per view would each be correct in isolation and could
     * still show three different answers mid-update.
     *
     * `today` is resolved per subscription for the same reason as the capture screen
     * (ADR-0018): it decides what counts as overdue, and it should not change under the
     * user while they are looking at it.
     */
    val uiState: StateFlow<GroupsUiState> = flow {
        val today = LocalDate.now(clock)
        emitAll(
            combine(
                repository.observeGroups(),
                repository.observeContributions(),
            ) { groups, contributions ->
                val byGroup = contributions.groupBy(Contribution::groupId)

                GroupsUiState(
                    today = today,
                    summaries = groups.map { group ->
                        val owed = byGroup[group.id].orEmpty().filter(Contribution::isOutstanding)
                        GroupSummary(
                            group = group,
                            outstanding = owed.map(Contribution::amount).sum(),
                            dueCount = owed.size,
                            nextDueDate = owed.minOfOrNull(Contribution::dueDate),
                        )
                    },
                    outstanding = contributions
                        .filter(Contribution::isOutstanding)
                        .sortedBy(Contribution::dueDate),
                    totalOutstanding = contributions
                        .filter(Contribution::isOutstanding)
                        .map(Contribution::amount)
                        .sum(),
                    historyByGroup = byGroup,
                )
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GroupsUiState(today = LocalDate.now(clock)),
    )

    // --- groups (FR4.1) -------------------------------------------------------

    fun saveGroup(edit: GroupEdit) {
        viewModelScope.launch {
            if (edit.id == null) {
                repository.createGroup(
                    name = edit.name,
                    type = edit.type,
                    penalty = edit.penalty,
                    reminderLeadDays = edit.reminderLeadDays,
                )
            } else {
                repository.updateGroup(
                    id = edit.id,
                    name = edit.name,
                    type = edit.type,
                    penalty = edit.penalty,
                    reminderLeadDays = edit.reminderLeadDays,
                )
            }
        }
    }

    /** Kept for the quick add-by-name field; the dialog covers type and penalty. */
    fun createGroup(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.createGroup(name = trimmed) }
    }

    // --- contributions (FR4.2, FR4.5) -----------------------------------------

    fun saveContribution(edit: ContributionEdit) {
        viewModelScope.launch {
            if (edit.id == null) {
                repository.addContribution(
                    groupId = edit.groupId,
                    amount = edit.amount,
                    dueDate = edit.dueDate,
                    note = edit.note,
                )
            } else {
                repository.updateContribution(
                    id = edit.id,
                    amount = edit.amount,
                    dueDate = edit.dueDate,
                    note = edit.note,
                )
            }
        }
    }

    fun markPaid(contributionId: Long) {
        viewModelScope.launch { repository.markPaid(contributionId) }
    }

    fun markMissed(contributionId: Long) {
        viewModelScope.launch { repository.markMissed(contributionId) }
    }

    fun reopen(contributionId: Long) {
        viewModelScope.launch { repository.revertToPending(contributionId) }
    }

    fun deleteContribution(contributionId: Long) {
        viewModelScope.launch { repository.deleteContribution(contributionId) }
    }
}

/** What the group dialog collects. A null [id] means a new group. */
data class GroupEdit(
    val id: Long?,
    val name: String,
    val type: GroupType,
    val penalty: Money?,
    val reminderLeadDays: Int,
)

/** What the contribution dialog collects. A null [id] means a new obligation. */
data class ContributionEdit(
    val id: Long?,
    val groupId: Long,
    val amount: Money,
    val dueDate: LocalDate,
    val note: String?,
)
