package com.sabimoni.core.data.model

import com.sabimoni.core.data.entity.Direction
import com.sabimoni.core.data.entity.GroupType
import com.sabimoni.core.data.entity.MessageSource
import com.sabimoni.core.data.entity.ParseStatus
import com.sabimoni.core.money.Money
import java.time.Instant

/**
 * Domain models. Repositories map entities to these before anything crosses into a
 * feature package, so persistence details stay in core/data — see
 * docs/adr/0004-mvvm-unidirectional-data-flow.md.
 */
data class CapturedMessage(
    val id: Long,
    val rawText: String,
    val source: MessageSource,
    val sentAt: Instant,
    val status: ParseStatus,
    val failureReason: String?,
    /** What the parser understood, empty until the message has been parsed (FR1.3). */
    val lineItems: List<ParsedLine> = emptyList(),
)

data class ParsedLine(
    val id: Long,
    val amount: Money,
    val direction: Direction,
    val category: String?,
    val note: String?,
)

data class MoneyGroup(
    val id: Long,
    val name: String,
    val type: GroupType,
    val penalty: Money?,
    val reminderLeadDays: Int,
)
