package com.sabimoni.core.data.model

import com.sabimoni.core.data.entity.Direction
import com.sabimoni.core.data.entity.GroupType
import com.sabimoni.core.data.entity.MessageSource
import com.sabimoni.core.data.entity.ParseStatus
import com.sabimoni.core.money.Money
import java.time.Instant
import java.time.LocalDate

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
    val lineItems: List<LoggedEntry> = emptyList(),
)

/**
 * One thing that was logged. The same type whether it came out of the parser or was typed
 * into the form (FR1.6) — the editor corrects both through one path, and the only thing
 * that differs is where it appears in the thread.
 */
data class LoggedEntry(
    val id: Long,
    val amount: Money,
    val direction: Direction,
    val categoryId: Long?,
    val categoryName: String?,
    val note: String?,
    val occurredOn: LocalDate,
    /** True for an entry derived from a MoMo SMS rather than something the user typed. */
    val autoDetected: Boolean,
    val loggedAt: Instant,
)

/**
 * One position in the capture thread (FR1.5). A manual entry has no message to sit under,
 * so it appears in the timeline in its own right rather than being invisible until
 * Reports — the thread is meant to show *everything* logged, not everything typed.
 */
sealed interface ThreadItem {
    /** Stable across reorderings, and unique across the two kinds. */
    val key: String
    val at: Instant

    data class Captured(val message: CapturedMessage) : ThreadItem {
        override val key: String get() = "message-${message.id}"
        override val at: Instant get() = message.sentAt
    }

    data class Manual(val entry: LoggedEntry) : ThreadItem {
        override val key: String get() = "entry-${entry.id}"
        override val at: Instant get() = entry.loggedAt
    }
}

/** The running total behind FR1.5. Income and expense kept apart so both can be shown. */
data class DayTotals(val income: Money, val expense: Money) {
    val net: Money get() = income - expense

    companion object {
        val EMPTY = DayTotals(Money.ZERO, Money.ZERO)
    }
}

/** A category as the editor's picker needs it: something to choose, never to invent. */
data class Category(val id: Long, val name: String)

data class MoneyGroup(
    val id: Long,
    val name: String,
    val type: GroupType,
    val penalty: Money?,
    val reminderLeadDays: Int,
)
