package com.sabimoni.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.sabimoni.core.data.entity.Direction
import com.sabimoni.core.data.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

data class CategoryTotal(
    val categoryId: Long?,
    val categoryName: String?,
    val totalXaf: Long,
)

/**
 * One logged entry joined to its category name — the shape the capture thread and the
 * editor both need. `messageId` is null for a manually entered row (FR1.6), which is what
 * distinguishes a standalone entry from a line the parser produced.
 */
data class TransactionRow(
    val id: Long,
    val messageId: Long?,
    val occurredOn: LocalDate,
    val amountXaf: Long,
    val direction: Direction,
    val categoryId: Long?,
    val categoryName: String?,
    val groupId: Long?,
    val groupName: String?,
    /** Non-null when this transaction settles an announced obligation (ADR-0025). */
    val groupContributionId: Long?,
    val note: String?,
    val autoDetected: Boolean,
    val createdAt: Instant,
)

@Dao
interface TransactionDao {

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Insert
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Upsert
    suspend fun upsert(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    /**
     * Deletes a whole selection in one statement, so observers — the running total above
     * all — see one change rather than counting down one entry at a time.
     */
    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /**
     * A correction (FR1.4) may change only these five fields. Written as one targeted
     * UPDATE rather than a read-modify-write so that `messageId`, `createdAt` and
     * `autoDetected` cannot be rewritten by accident: how an entry arrived is history, not
     * something a correction is allowed to edit. See
     * docs/adr/0018-corrections-and-manual-entry.md.
     */
    @Query(
        """
        UPDATE transactions
        SET amountXaf = :amountXaf,
            direction = :direction,
            categoryId = :categoryId,
            groupId = :groupId,
            note = :note,
            occurredOn = :occurredOn
        WHERE id = :id
        """,
    )
    suspend fun applyCorrection(
        id: Long,
        amountXaf: Long,
        direction: Direction,
        categoryId: Long?,
        groupId: Long?,
        note: String?,
        occurredOn: LocalDate,
    )

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun byId(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE occurredOn = :day ORDER BY createdAt ASC")
    fun observeForDay(day: LocalDate): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE messageId = :messageId ORDER BY id ASC")
    fun observeForMessage(messageId: Long): Flow<List<TransactionEntity>>

    @Query(
        "SELECT * FROM transactions WHERE occurredOn BETWEEN :from AND :to " +
            "ORDER BY occurredOn DESC, createdAt DESC",
    )
    fun observeInRange(from: LocalDate, to: LocalDate): Flow<List<TransactionEntity>>

    @Query(
        "SELECT COALESCE(SUM(amountXaf), 0) FROM transactions " +
            "WHERE direction = :direction AND occurredOn BETWEEN :from AND :to",
    )
    fun observeTotal(direction: Direction, from: LocalDate, to: LocalDate): Flow<Long>

    /**
     * Everything ever logged, income minus expense.
     *
     * Summed in SQL rather than over a list of rows, because unlike the day total this one
     * grows without bound. It is only as true as what has been entered, which is why the
     * screen labels where it comes from rather than claiming to know a real account
     * balance.
     */
    @Query(
        "SELECT COALESCE(SUM(CASE WHEN direction = 'INCOME' THEN amountXaf ELSE -amountXaf END), 0) " +
            "FROM transactions",
    )
    fun observeBalance(): Flow<Long>

    /**
     * Every logged entry, parser-produced and manual alike. One query rather than two so
     * the thread cannot be assembled from two lists that disagree about the same row.
     */
    @Query(
        """
        SELECT t.id AS id,
               t.messageId AS messageId,
               t.occurredOn AS occurredOn,
               t.amountXaf AS amountXaf,
               t.direction AS direction,
               t.categoryId AS categoryId,
               c.name AS categoryName,
               t.groupId AS groupId,
               g.name AS groupName,
               t.groupContributionId AS groupContributionId,
               t.note AS note,
               t.autoDetected AS autoDetected,
               t.createdAt AS createdAt
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.categoryId
        LEFT JOIN money_groups g ON g.id = t.groupId
        ORDER BY t.id ASC
        """,
    )
    fun observeRows(): Flow<List<TransactionRow>>

    @Query(
        """
        SELECT t.categoryId AS categoryId,
               c.name AS categoryName,
               COALESCE(SUM(t.amountXaf), 0) AS totalXaf
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.categoryId
        WHERE t.direction = 'EXPENSE' AND t.occurredOn BETWEEN :from AND :to
        GROUP BY t.categoryId
        ORDER BY totalXaf DESC
        """,
    )
    fun observeSpendByCategory(from: LocalDate, to: LocalDate): Flow<List<CategoryTotal>>
}
