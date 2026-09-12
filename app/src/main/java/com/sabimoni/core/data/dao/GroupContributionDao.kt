package com.sabimoni.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.sabimoni.core.data.entity.ContributionStatus
import com.sabimoni.core.data.entity.GroupContributionEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * A contribution joined to the group that announced it. Every group view needs the group's
 * name to say anything useful, and the reminder needs its penalty (FR4.4), so the join
 * belongs here rather than in three separate lookups.
 */
data class ContributionRow(
    val id: Long,
    val groupId: Long,
    val groupName: String,
    val amountXaf: Long,
    val dueDate: LocalDate,
    val status: ContributionStatus,
    val paidDate: LocalDate?,
    val note: String?,
    val penaltyXaf: Long?,
    val reminderLeadDays: Int,
)

private const val CONTRIBUTION_COLUMNS = """
    SELECT c.id AS id,
           c.groupId AS groupId,
           g.name AS groupName,
           c.amountXaf AS amountXaf,
           c.dueDate AS dueDate,
           c.status AS status,
           c.paidDate AS paidDate,
           c.note AS note,
           g.penaltyXaf AS penaltyXaf,
           g.reminderLeadDays AS reminderLeadDays
    FROM group_contributions c
    INNER JOIN money_groups g ON g.id = c.groupId
"""

@Dao
interface GroupContributionDao {

    @Upsert
    suspend fun upsert(contribution: GroupContributionEntity): Long

    @Query("SELECT * FROM group_contributions WHERE id = :id")
    suspend fun byId(id: Long): GroupContributionEntity?

    @Query("DELETE FROM group_contributions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM group_contributions WHERE status = 'PENDING' ORDER BY dueDate ASC")
    fun observeOutstanding(): Flow<List<GroupContributionEntity>>

    @Query("SELECT * FROM group_contributions WHERE groupId = :groupId ORDER BY dueDate DESC")
    fun observeForGroup(groupId: Long): Flow<List<GroupContributionEntity>>

    /** Still-owed obligations for one group, for re-arming reminders after an edit. */
    @Query(
        "SELECT * FROM group_contributions " +
            "WHERE groupId = :groupId AND status = 'PENDING' ORDER BY dueDate ASC",
    )
    suspend fun pendingForGroup(groupId: Long): List<GroupContributionEntity>

    @Query("SELECT COALESCE(SUM(amountXaf), 0) FROM group_contributions WHERE status = 'PENDING'")
    fun observeTotalOutstanding(): Flow<Long>

    @Query("UPDATE group_contributions SET status = :status, paidDate = :paidDate WHERE id = :id")
    suspend fun updateStatus(id: Long, status: ContributionStatus, paidDate: LocalDate?)

    /**
     * Every contribution ever, newest due date first.
     *
     * One query rather than separate ones for "what I owe" (FR4.6), each group's totals
     * and each group's history (FR4.5). All three are views of the same small list — this
     * is one person's social obligations, not a ledger — and deriving them from one read
     * means they cannot disagree with each other about the same row.
     */
    @Query("$CONTRIBUTION_COLUMNS ORDER BY c.dueDate DESC")
    fun observeAllRows(): Flow<List<ContributionRow>>

    /** For the reminder, which needs the group's name and its penalty in one read. */
    @Query("$CONTRIBUTION_COLUMNS WHERE c.id = :id")
    suspend fun rowById(id: Long): ContributionRow?

    /**
     * Whether this group already has an obligation for that deadline.
     *
     * How the recurrence roll-forward stays idempotent: the daily worker recomputes every
     * deadline a schedule has reached and creates only the missing ones, so running twice
     * — or catching up after a week with the app closed — cannot duplicate anything.
     * `(groupId, dueDate)` is the period's identity; no extra column needed (ADR-0029).
     */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM group_contributions " +
            "WHERE groupId = :groupId AND dueDate = :dueDate)",
    )
    suspend fun existsFor(groupId: Long, dueDate: LocalDate): Boolean

    /** Still unpaid with the deadline behind us — the fine is running (FR4.4). */
    @Query("$CONTRIBUTION_COLUMNS WHERE c.status = 'PENDING' AND c.dueDate < :today")
    suspend fun overdue(today: LocalDate): List<ContributionRow>

    /** Unpaid and falling due on exactly this day — the lead-time warning. */
    @Query("$CONTRIBUTION_COLUMNS WHERE c.status = 'PENDING' AND c.dueDate = :dueDate")
    suspend fun pendingDueOn(dueDate: LocalDate): List<ContributionRow>

    /**
     * The contributions settled by any of these transactions. Used before deleting a
     * transaction, so a contribution cannot stay marked Paid once the payment that
     * settled it is gone — see ADR-0026.
     */
    @Query(
        "SELECT DISTINCT groupContributionId FROM transactions " +
            "WHERE id IN (:transactionIds) AND groupContributionId IS NOT NULL",
    )
    suspend fun contributionsSettledBy(transactionIds: List<Long>): List<Long>

    /** How many transactions still point at this contribution. */
    @Query("SELECT COUNT(*) FROM transactions WHERE groupContributionId = :contributionId")
    suspend fun paymentCountFor(contributionId: Long): Int
}
