package com.sabimoni.core.data.repository

import com.sabimoni.core.data.dao.MessageDao
import com.sabimoni.core.data.dao.TransactionDao
import com.sabimoni.core.data.dao.TransactionRow
import com.sabimoni.core.data.entity.MessageEntity
import com.sabimoni.core.data.entity.MessageSource
import com.sabimoni.core.data.entity.ParseStatus
import com.sabimoni.core.data.model.CapturedMessage
import com.sabimoni.core.data.model.LoggedEntry
import com.sabimoni.core.data.model.ThreadItem
import com.sabimoni.core.money.Money
import com.sabimoni.core.parse.ParseScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaptureRepository @Inject constructor(
    private val messageDao: MessageDao,
    private val transactionDao: TransactionDao,
    private val parseScheduler: ParseScheduler,
    private val clock: Clock,
) {

    /**
     * The capture thread (FR1.5): every message with the lines it produced, interleaved
     * with entries typed straight into the form, in the order they were logged.
     *
     * Manual entries are merged in here rather than shown on a separate screen because
     * the spec asks for a *single* thread showing everything logged — an entry that is
     * invisible in the place you just logged it reads as a lost entry.
     */
    fun observeThread(): Flow<List<ThreadItem>> = combine(
        messageDao.observeAll(),
        transactionDao.observeRows(),
    ) { messages, rows ->
        val byMessage = rows.groupBy(TransactionRow::messageId)

        val captured = messages.map { message ->
            ThreadItem.Captured(message.toDomain(byMessage[message.id].orEmpty()))
        }
        val manual = byMessage[null].orEmpty().map { row ->
            ThreadItem.Manual(row.toDomain())
        }

        (captured + manual).sortedBy(ThreadItem::at)
    }

    fun observePendingCount(): Flow<Int> = messageDao.observePendingCount()

    /**
     * Writes the message locally and returns immediately, then asks for the queue to be
     * drained — see docs/adr/0015-offline-capture-parse-queue.md. Scheduling after the
     * insert, not before, so the worker can never race ahead of the row it is meant to
     * find.
     */
    suspend fun capture(rawText: String, source: MessageSource = MessageSource.TYPED): Long {
        val id = messageDao.insert(
            MessageEntity(
                rawText = rawText,
                source = source,
                sentAt = Instant.now(clock),
                status = ParseStatus.PENDING_PARSE,
            ),
        )
        parseScheduler.schedule()
        return id
    }

    /**
     * Tries a message again, right now.
     *
     * Covers both states the user can be looking at: a FAILED message is put back on the
     * queue first, and one that is still PENDING is simply run immediately rather than
     * waiting out a backoff delay it cannot see (ADR-0021).
     *
     * A PARSED message schedules nothing — `requeueFailed` refuses to move it, so a stray
     * tap cannot re-parse it and duplicate its entries.
     */
    suspend fun retry(messageId: Long) {
        val requeued = messageDao.requeueFailed(messageId) > 0
        if (requeued || messageDao.statusOf(messageId) == ParseStatus.PENDING_PARSE) {
            parseScheduler.scheduleNow()
        }
    }
}

private fun MessageEntity.toDomain(rows: List<TransactionRow>) = CapturedMessage(
    id = id,
    rawText = rawText,
    source = source,
    sentAt = sentAt,
    status = status,
    failureReason = failureReason,
    lineItems = rows.map(TransactionRow::toDomain),
)

private fun TransactionRow.toDomain() = LoggedEntry(
    id = id,
    amount = Money(amountXaf),
    direction = direction,
    categoryId = categoryId,
    categoryName = categoryName,
    groupId = groupId,
    groupName = groupName,
    note = note,
    occurredOn = occurredOn,
    autoDetected = autoDetected,
    loggedAt = createdAt,
    settlesContributionId = groupContributionId,
)
