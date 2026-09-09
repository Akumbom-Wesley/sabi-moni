package com.sabimoni.core.data.repository

import com.sabimoni.core.data.dao.MessageDao
import com.sabimoni.core.data.entity.MessageEntity
import com.sabimoni.core.data.entity.MessageSource
import com.sabimoni.core.data.entity.ParseStatus
import com.sabimoni.core.data.model.CapturedMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaptureRepository @Inject constructor(
    private val messageDao: MessageDao,
) {

    fun observeThread(): Flow<List<CapturedMessage>> =
        messageDao.observeAll().map { messages -> messages.map(MessageEntity::toDomain) }

    fun observePendingCount(): Flow<Int> = messageDao.observePendingCount()

    /**
     * Writes the message locally and returns immediately. Parsing is a separate queued
     * step — see docs/adr/0015-offline-capture-parse-queue.md.
     */
    suspend fun capture(rawText: String, source: MessageSource = MessageSource.TYPED): Long =
        messageDao.insert(
            MessageEntity(
                rawText = rawText,
                source = source,
                sentAt = Instant.now(),
                status = ParseStatus.PENDING_PARSE,
            ),
        )
}

private fun MessageEntity.toDomain() = CapturedMessage(
    id = id,
    rawText = rawText,
    source = source,
    sentAt = sentAt,
    status = status,
    failureReason = failureReason,
)
