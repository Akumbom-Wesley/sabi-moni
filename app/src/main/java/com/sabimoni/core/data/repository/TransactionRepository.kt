package com.sabimoni.core.data.repository

import androidx.room.withTransaction
import com.sabimoni.core.ai.TransactionDraft
import com.sabimoni.core.data.SabiMoniDatabase
import com.sabimoni.core.data.dao.CategoryDao
import com.sabimoni.core.data.dao.MessageDao
import com.sabimoni.core.data.dao.TransactionDao
import com.sabimoni.core.data.entity.MessageEntity
import com.sabimoni.core.data.entity.MessageSource
import com.sabimoni.core.data.entity.TransactionEntity
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val database: SabiMoniDatabase,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val messageDao: MessageDao,
    private val clock: Clock,
) {

    /**
     * Persists what the parser understood and marks the message parsed, in one database
     * transaction. Splitting the two would let a crash leave transactions written against
     * a message still queued as pending, which the worker would then parse again —
     * duplicating every entry. See docs/adr/0017-parse-worker-and-failure-policy.md.
     *
     * A message that yields no entries is still PARSED, not FAILED: "happy birthday mum"
     * is a message with nothing to log, not an error.
     */
    suspend fun commitParse(message: MessageEntity, drafts: List<TransactionDraft>) {
        val now = Instant.now(clock)
        val today = LocalDate.now(clock)

        database.withTransaction {
            val entities = drafts
                // A zero or negative amount is not a thing that happened. The response
                // schema cannot express "positive", so it is enforced here.
                .filter { it.amountXaf > 0L }
                .map { draft -> draft.toEntity(message, today, now) }

            if (entities.isNotEmpty()) {
                transactionDao.insertAll(entities)
            }
            messageDao.markParsed(message.id, now)
        }
    }

    suspend fun markFailed(messageId: Long, reason: String) {
        messageDao.markFailed(messageId, reason)
    }

    private suspend fun TransactionDraft.toEntity(
        message: MessageEntity,
        today: LocalDate,
        now: Instant,
    ) = TransactionEntity(
        messageId = message.id,
        occurredOn = occurredOn ?: today,
        amountXaf = amountXaf,
        direction = direction,
        // Only ever resolved against categories that already exist — the model is not
        // allowed to invent taxonomy the user would then have to maintain (ADR-0017).
        categoryId = category?.let { categoryDao.byNameIgnoreCase(it)?.id },
        note = note,
        moneySource = moneySource,
        autoDetected = message.source == MessageSource.SMS,
        createdAt = now,
    )
}
