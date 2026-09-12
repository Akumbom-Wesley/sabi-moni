package com.sabimoni.core.data.repository

import androidx.room.withTransaction
import com.sabimoni.core.data.SabiMoniDatabase
import com.sabimoni.core.data.dao.ContributionRow
import com.sabimoni.core.data.dao.GroupContributionDao
import com.sabimoni.core.data.dao.GroupDao
import com.sabimoni.core.data.dao.TransactionDao
import com.sabimoni.core.data.entity.ContributionStatus
import com.sabimoni.core.data.entity.Direction
import com.sabimoni.core.data.entity.GroupContributionEntity
import com.sabimoni.core.data.entity.GroupEntity
import com.sabimoni.core.data.entity.TransactionEntity
import com.sabimoni.core.data.entity.deadlinesThrough
import com.sabimoni.core.data.model.Contribution
import com.sabimoni.core.data.model.MoneyGroup
import com.sabimoni.core.data.model.RecurrenceSchedule
import com.sabimoni.core.money.Money
import com.sabimoni.core.reminder.ObligationScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Groups and the obligations they announce (FR4.x) — the pillar that exists to stop
 * penalty loss.
 *
 * Holds the whole contribution lifecycle rather than splitting it, because the steps are
 * not independent: a recurring schedule materialises obligations, settling one writes a
 * transaction, and deleting that payment puts the obligation back.
 */
@Singleton
class GroupRepository @Inject constructor(
    private val database: SabiMoniDatabase,
    private val groupDao: GroupDao,
    private val contributionDao: GroupContributionDao,
    private val transactionDao: TransactionDao,
    private val obligations: ObligationScheduler,
    private val clock: Clock,
) {

    // --- groups ---------------------------------------------------------------

    fun observeGroups(): Flow<List<MoneyGroup>> =
        groupDao.observeActive().map { groups -> groups.map(GroupEntity::toDomain) }

    fun observeTotalOutstanding(): Flow<Money> =
        contributionDao.observeTotalOutstanding().map(::Money)

    suspend fun createGroup(
        name: String,
        penalty: Money? = null,
        reminderLeadDays: Int = DEFAULT_LEAD_DAYS,
        recurrence: RecurrenceSchedule? = null,
    ): Long {
        val id = groupDao.upsert(
            GroupEntity(
                name = name,
                penaltyXaf = penalty?.xaf,
                reminderLeadDays = reminderLeadDays,
                recurrenceUnit = recurrence?.unit,
                recurrenceAmountXaf = recurrence?.amount?.xaf,
                recurrenceAnchor = recurrence?.anchor,
            ),
        )
        // A standing commitment should show its first obligation immediately, not
        // tomorrow morning when the daily check next runs.
        if (recurrence != null) obligations.checkNow()
        return id
    }

    /**
     * Edits a group in place.
     *
     * Asks for an immediate check afterwards, because the schedule, the amount and the lead
     * time all change what is owed and when the user expects to hear about it. A setting
     * that only takes effect tomorrow is a setting that looks broken.
     */
    suspend fun updateGroup(
        id: Long,
        name: String,
        penalty: Money?,
        reminderLeadDays: Int,
        recurrence: RecurrenceSchedule? = null,
    ) {
        val existing = groupDao.byId(id) ?: return
        groupDao.upsert(
            existing.copy(
                name = name,
                penaltyXaf = penalty?.xaf,
                reminderLeadDays = reminderLeadDays,
                recurrenceUnit = recurrence?.unit,
                recurrenceAmountXaf = recurrence?.amount?.xaf,
                recurrenceAnchor = recurrence?.anchor,
            ),
        )
        obligations.checkNow()
    }

    // --- recurrence -----------------------------------------------------------

    /**
     * Materialises every deadline each recurring schedule has reached, and returns how many
     * obligations that created — FR4.2 without re-entering a standing commitment
     * (ADR-0029).
     *
     * Idempotent by `(groupId, dueDate)`: it recomputes the whole series each run and
     * creates only what is missing, so running twice, or catching up after a fortnight with
     * the app closed, cannot duplicate a period.
     *
     * Deadlines are generated up to *today plus the lead time*, so the upcoming one exists
     * early enough to be warned about.
     */
    suspend fun rollForwardRecurring(today: LocalDate = LocalDate.now(clock)): Int {
        var created = 0

        groupDao.recurring().forEach { group ->
            val unit = group.recurrenceUnit ?: return@forEach
            val amount = group.recurrenceAmountXaf ?: return@forEach
            val anchor = group.recurrenceAnchor ?: return@forEach

            val deadlines = deadlinesThrough(
                anchor = anchor,
                unit = unit,
                through = today.plusDays(group.reminderLeadDays.toLong()),
            )

            deadlines.forEach { dueDate ->
                if (contributionDao.existsFor(group.id, dueDate)) return@forEach
                contributionDao.upsert(
                    GroupContributionEntity(
                        groupId = group.id,
                        amountXaf = amount,
                        dueDate = dueDate,
                        status = ContributionStatus.PENDING,
                    ),
                )
                created++
            }
        }

        return created
    }

    /**
     * Obligations the daily check should consider notifying about: unpaid, and either
     * inside their lead window or already past due.
     *
     * Whether each one actually notifies — once for the warning, daily for the overdue nag
     * — is the worker's call, since only it knows what has already been said today.
     */
    suspend fun dueForReminder(today: LocalDate = LocalDate.now(clock)): List<Contribution> {
        val overdue = contributionDao.overdue(today).map(ContributionRow::toDomain)

        // The warning day is `leadDays` before the deadline. Anything from that day up to
        // the deadline itself counts as inside the window, which also catches an obligation
        // relayed so late that its warning day had already gone.
        val upcoming = groupDao.active().flatMap { group ->
            (0..group.reminderLeadDays).flatMap { offset ->
                contributionDao.pendingDueOn(today.plusDays(offset.toLong()))
                    .filter { it.groupId == group.id }
                    .map(ContributionRow::toDomain)
            }
        }

        return (overdue + upcoming).distinctBy(Contribution::id)
    }

    // --- contributions --------------------------------------------------------

    /**
     * Every contribution, newest first. The single read that "what I owe" (FR4.6), each
     * group's outstanding total and each group's history (FR4.5) are all derived from.
     */
    fun observeContributions(): Flow<List<Contribution>> =
        contributionDao.observeAllRows().map { rows -> rows.map(ContributionRow::toDomain) }

    suspend fun contribution(id: Long): Contribution? = contributionDao.rowById(id)?.toDomain()

    /** Logs a one-off announced contribution (FR4.2). */
    suspend fun addContribution(
        groupId: Long,
        amount: Money,
        dueDate: LocalDate,
        note: String? = null,
    ): Long {
        groupDao.byId(groupId) ?: return NO_ID
        val id = contributionDao.upsert(
            GroupContributionEntity(
                groupId = groupId,
                amountXaf = amount.xaf,
                dueDate = dueDate,
                status = ContributionStatus.PENDING,
                note = note?.takeIf(String::isNotBlank),
            ),
        )
        // Checked now rather than left to the morning: an obligation relayed late may
        // already be inside its lead window, or past due.
        obligations.checkNow()
        return id
    }

    suspend fun updateContribution(
        id: Long,
        amount: Money,
        dueDate: LocalDate,
        note: String?,
    ) {
        val existing = contributionDao.byId(id) ?: return
        contributionDao.upsert(
            existing.copy(
                amountXaf = amount.xaf,
                dueDate = dueDate,
                note = note?.takeIf(String::isNotBlank),
            ),
        )
        obligations.checkNow()
    }

    /**
     * Marks a contribution paid and records the money leaving, in one database
     * transaction (FR4.5).
     *
     * `groupId` on the transaction is **derived from the contribution**, never passed in,
     * so the two group references cannot disagree — the invariant ADR-0025 depends on.
     *
     * No category is assigned. The group axis carries this attribution structurally, and
     * Reports treats category and group as separate axes (FR5.1), so stamping a category
     * too would be a second copy of the same fact.
     *
     * Records the contribution amount only. A fine run up by paying late is shown in the
     * owed view and named in the reminder, but is not auto-recorded as money spent —
     * whether it was actually charged is the user's to say (ADR-0029).
     */
    suspend fun markPaid(contributionId: Long, paidOn: LocalDate = LocalDate.now(clock)) {
        val contribution = contributionDao.byId(contributionId) ?: return
        val now = Instant.now(clock)

        database.withTransaction {
            transactionDao.insert(
                TransactionEntity(
                    occurredOn = paidOn,
                    amountXaf = contribution.amountXaf,
                    direction = Direction.EXPENSE,
                    groupId = contribution.groupId,
                    groupContributionId = contribution.id,
                    note = contribution.note,
                    createdAt = now,
                ),
            )
            contributionDao.updateStatus(contributionId, ContributionStatus.PAID, paidOn)
        }

        obligations.dismiss(contributionId)
    }

    /** Marks it missed. No transaction: nothing moved, which is the whole problem. */
    suspend fun markMissed(contributionId: Long) {
        contributionDao.updateStatus(contributionId, ContributionStatus.MISSED, paidDate = null)
        obligations.dismiss(contributionId)
    }

    /**
     * Puts a settled obligation back on the books.
     *
     * Called both when the user undoes a Paid/Missed mark and when the transaction that
     * paid it is deleted — see [TransactionRepository.deleteEntries]. A contribution still
     * reading Paid after its payment has been deleted is the owed view lying (ADR-0026).
     */
    suspend fun revertToPending(contributionId: Long) {
        contributionDao.byId(contributionId) ?: return
        contributionDao.updateStatus(
            contributionId,
            ContributionStatus.PENDING,
            paidDate = null,
        )
        obligations.checkNow()
    }

    suspend fun deleteContribution(contributionId: Long) {
        obligations.dismiss(contributionId)
        contributionDao.deleteById(contributionId)
    }

    private companion object {
        const val DEFAULT_LEAD_DAYS = 2
        const val NO_ID = -1L
    }
}

private fun GroupEntity.toDomain(): MoneyGroup {
    val unit = recurrenceUnit
    val amount = recurrenceAmountXaf
    val anchor = recurrenceAnchor

    return MoneyGroup(
        id = id,
        name = name,
        penalty = penaltyXaf?.let(::Money),
        reminderLeadDays = reminderLeadDays,
        // All three or nothing: a half-configured schedule cannot produce a deadline, so it
        // reads as no schedule rather than as a broken one.
        recurrence = if (unit != null && amount != null && anchor != null) {
            RecurrenceSchedule(unit = unit, amount = Money(amount), anchor = anchor)
        } else {
            null
        },
    )
}

private fun ContributionRow.toDomain() = Contribution(
    id = id,
    groupId = groupId,
    groupName = groupName,
    amount = Money(amountXaf),
    dueDate = dueDate,
    status = status,
    paidDate = paidDate,
    note = note,
    penalty = penaltyXaf?.let(::Money),
)
