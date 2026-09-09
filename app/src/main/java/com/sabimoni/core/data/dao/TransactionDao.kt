package com.sabimoni.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.sabimoni.core.data.entity.Direction
import com.sabimoni.core.data.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class CategoryTotal(
    val categoryId: Long?,
    val categoryName: String?,
    val totalXaf: Long,
)

/** One parsed line, joined to its category name for display in the capture thread. */
data class MessageLineItem(
    val messageId: Long,
    val transactionId: Long,
    val amountXaf: Long,
    val direction: Direction,
    val categoryName: String?,
    val note: String?,
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

    @Query(
        """
        SELECT t.messageId AS messageId,
               t.id AS transactionId,
               t.amountXaf AS amountXaf,
               t.direction AS direction,
               c.name AS categoryName,
               t.note AS note
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.categoryId
        WHERE t.messageId IS NOT NULL
        ORDER BY t.id ASC
        """,
    )
    fun observeLineItems(): Flow<List<MessageLineItem>>

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
