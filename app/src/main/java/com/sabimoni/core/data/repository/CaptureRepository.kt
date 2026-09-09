package com.sabimoni.core.data.repository

import com.sabimoni.core.data.dao.MessageDao
import com.sabimoni.core.data.dao.MessageLineItem
import com.sabimoni.core.data.dao.TransactionDao
import com.sabimoni.core.data.entity.MessageEntity
import com.sabimoni.core.data.entity.MessageSource
import com.sabimoni.core.data.entity.ParseStatus
import com.sabimoni.core.data.model.CapturedMessage
import com.sabimoni.core.data.model.ParsedLine
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

    fun observeThread(): Flow<List<CapturedMessage>> = combine(
        messageDao.observeAll(),
        transactionDao.observeLineItems(),
    ) { messages, lineItems ->
        val byMessage = lineItems.groupBy(MessageLineItem::messageId)
        messages.map { message ->
            message.toDomain(byMessage[message.id].orEmpty())
        }
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
}

private fun MessageEntity.toDomain(lineItems: List<MessageLineItem>) = CapturedMessage(
    id = id,
    rawText = rawText,
    source = source,
    sentAt = sentAt,
    status = status,
    failureReason = failureReason,
    lineItems = lineItems.map { item ->
        ParsedLine(
            id = item.transactionId,
            amount = Money(item.amountXaf),
            direction = item.direction,
            category = item.categoryName,
            note = item.note,
        )
    },
)
