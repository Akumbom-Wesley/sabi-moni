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
import com.sabimoni.core.data.model.Contribution
import com.sabimoni.core.data.model.MoneyGroup
import com.sabimoni.core.money.Money
import com.sabimoni.core.reminder.ReminderScheduler
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
 * not independent: creating one arms a reminder, settling one writes a transaction and
 * disarms that reminder, and deleting the payment puts both back.
 */
@Singleton
class GroupRepository @Inject constructor(
    private val database: SabiMoniDatabase,
    private val groupDao: GroupDao,
    private val contributionDao: GroupContributionDao,
    private val transactionDao: TransactionDao,
    private val reminderScheduler: ReminderScheduler,
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
    ): Long = groupDao.upsert(
        GroupEntity(
            name = name,
            penaltyXaf = penalty?.xaf,
            reminderLeadDays = reminderLeadDays,
        ),
    )

    /**
     * Edits a group in place. Changing `reminderLeadDays` re-arms every outstanding
     * reminder for it — a lead time that only applies to obligations announced after the
     * change would be a setting that silently does not work.
     */
    suspend fun updateGroup(
        id: Long,
        name: String,
        penalty: Money?,
        reminderLeadDays: Int,
    ) {
        val existing = groupDao.byId(id) ?: return
        groupDao.upsert(
            existing.copy(
                name = name,
                penaltyXaf = penalty?.xaf,
                reminderLeadDays = reminderLeadDays,
            ),
        )
        if (existing.reminderLeadDays != reminderLeadDays) {
            rearmRemindersFor(id, reminderLeadDays)
        }
    }

    // --- contributions --------------------------------------------------------

    /**
     * Every contribution, newest first. The single read that "what I owe" (FR4.6), each
     * group's outstanding total and each group's history (FR4.5) are all derived from.
     */
    fun observeContributions(): Flow<List<Contribution>> =
        contributionDao.observeAllRows().map { rows -> rows.map(ContributionRow::toDomain) }

    suspend fun contribution(id: Long): Contribution? = contributionDao.rowById(id)?.toDomain()

    /**
     * Logs an announced contribution (FR4.2) and arms its reminder (FR4.3).
     *
     * The reminder is scheduled here rather than by the caller, so there is no way to
     * record an obligation and forget to warn about it.
     */
    suspend fun addContribution(
        groupId: Long,
        amount: Money,
        dueDate: LocalDate,
        note: String? = null,
    ): Long {
        val group = groupDao.byId(groupId) ?: return NO_ID
        val id = contributionDao.upsert(
            GroupContributionEntity(
                groupId = groupId,
                amountXaf = amount.xaf,
                dueDate = dueDate,
                status = ContributionStatus.PENDING,
                note = note?.takeIf(String::isNotBlank),
            ),
        )
        reminderScheduler.schedule(id, reminderInstant(dueDate, group.reminderLeadDays))
        return id
    }

    /** Edits an obligation and moves its reminder with it. */
    suspend fun updateContribution(
        id: Long,
        amount: Money,
        dueDate: LocalDate,
        note: String?,
    ) {
        val existing = contributionDao.byId(id) ?: return
        val group = groupDao.byId(existing.groupId) ?: return
        contributionDao.upsert(
            existing.copy(
                amountXaf = amount.xaf,
                dueDate = dueDate,
                note = note?.takeIf(String::isNotBlank),
            ),
        )
        if (existing.status == ContributionStatus.PENDING) {
            reminderScheduler.schedule(id, reminderInstant(dueDate, group.reminderLeadDays))
        }
    }

    /**
     * Marks a contribution paid and records the money leaving, in one database
     * transaction (FR4.5).
     *
     * `groupId` on the transaction is **derived from the contribution**, never passed in,
     * so the two group references cannot disagree — the invariant ADR-0025 depends on.
     *
     * No category is assigned. The group axis now carries this attribution structurally,
     * and Reports treats category and group as separate axes (FR5.1), so stamping a
     * category too would be a second copy of the same fact.
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

        reminderScheduler.cancel(contributionId)
    }

    /** Marks it missed. No transaction: nothing moved, which is the whole problem. */
    suspend fun markMissed(contributionId: Long) {
        contributionDao.updateStatus(contributionId, ContributionStatus.MISSED, paidDate = null)
        reminderScheduler.cancel(contributionId)
    }

    /**
     * Puts a settled obligation back on the books and re-arms its reminder.
     *
     * Called both when the user undoes a Paid/Missed mark and when the transaction that
     * paid it is deleted — see [TransactionRepository.deleteEntries]. A contribution still
     * reading Paid after its payment has been deleted is the owed view lying (ADR-0026).
     */
    suspend fun revertToPending(contributionId: Long) {
        val contribution = contributionDao.byId(contributionId) ?: return
        val group = groupDao.byId(contribution.groupId) ?: return

        contributionDao.updateStatus(
            contributionId,
            ContributionStatus.PENDING,
            paidDate = null,
        )
        reminderScheduler.schedule(
            contributionId,
            reminderInstant(contribution.dueDate, group.reminderLeadDays),
        )
    }

    suspend fun deleteContribution(contributionId: Long) {
        reminderScheduler.cancel(contributionId)
        contributionDao.deleteById(contributionId)
    }

    // --- reminders ------------------------------------------------------------

    private suspend fun rearmRemindersFor(groupId: Long, leadDays: Int) {
        contributionDao.pendingForGroup(groupId).forEach { contribution ->
            reminderScheduler.schedule(
                contribution.id,
                reminderInstant(contribution.dueDate, leadDays),
            )
        }
    }

    /**
     * `reminderLeadDays` before the due date, at [REMINDER_HOUR] local time.
     *
     * A fixed morning hour rather than the moment of scheduling: a reminder that arrives
     * at 02:00 because that is when the WhatsApp message was relayed is a reminder you
     * sleep through. Morning, because paying is a daytime errand — unlike capture, which
     * is deliberately an evening ritual.
     */
    private fun reminderInstant(dueDate: LocalDate, leadDays: Int): Instant =
        dueDate.minusDays(leadDays.toLong())
            .atTime(REMINDER_HOUR, 0)
            .atZone(clock.zone)
            .toInstant()

    private companion object {
        const val DEFAULT_LEAD_DAYS = 2
        const val REMINDER_HOUR = 9
        const val NO_ID = -1L
    }
}

private fun GroupEntity.toDomain() = MoneyGroup(
    id = id,
    name = name,
    penalty = penaltyXaf?.let(::Money),
    reminderLeadDays = reminderLeadDays,
)

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
