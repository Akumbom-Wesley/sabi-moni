package com.sabimoni.core.data.model

import com.sabimoni.core.data.entity.ContributionStatus
import com.sabimoni.core.data.entity.Direction
import com.sabimoni.core.data.entity.MessageSource
import com.sabimoni.core.data.entity.ParseStatus
import com.sabimoni.core.data.entity.RecurrenceUnit
import com.sabimoni.core.money.Money
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

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
    val groupId: Long?,
    val groupName: String?,
    val note: String?,
    val occurredOn: LocalDate,
    /** True for an entry derived from a MoMo SMS rather than something the user typed. */
    val autoDetected: Boolean,
    val loggedAt: Instant,
    /** Set when this entry settles an announced obligation. */
    val settlesContributionId: Long? = null,
) {
    /**
     * A payment for an announced obligation takes its group from that obligation, so the
     * editor shows the group as fixed rather than letting a correction break the
     * invariant ADR-0025 relies on. Change the obligation instead.
     */
    val isGroupFixed: Boolean get() = settlesContributionId != null
}

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

/**
 * Everything the entry editor's pickers offer. One type because they are only ever wanted
 * together, and because the capture screen's state would otherwise need a sixth flow —
 * past the arity `combine` gives type safety for.
 */
data class EditorOptions(
    val categories: List<Category> = emptyList(),
    val groups: List<MoneyGroup> = emptyList(),
)

/**
 * A standing commitment: "at least 1000 every month, from the 26th".
 *
 * All three parts are required together — an amount with no frequency, or a frequency with
 * no anchor, cannot produce a deadline — which is why they travel as one object rather than
 * three nullable fields on the group (ADR-0029).
 */
data class RecurrenceSchedule(
    val unit: RecurrenceUnit,
    val amount: Money,
    /** The first deadline; later ones are derived by advancing from here. */
    val anchor: LocalDate,
)

data class MoneyGroup(
    val id: Long,
    val name: String,
    val penalty: Money?,
    val reminderLeadDays: Int,
    /** Null for a group whose demands are announced ad hoc rather than on a schedule. */
    val recurrence: RecurrenceSchedule? = null,
)

/**
 * One announced obligation (FR4.2). Carries its group's name and penalty because nothing
 * useful can be said about a contribution without them.
 */
data class Contribution(
    val id: Long,
    val groupId: Long,
    val groupName: String,
    val amount: Money,
    val dueDate: LocalDate,
    val status: ContributionStatus,
    val paidDate: LocalDate?,
    val note: String?,
    /** What missing it costs — the motivation FR4.4 asks the reminder to surface. */
    val penalty: Money?,
) {
    val isOutstanding: Boolean get() = status == ContributionStatus.PENDING

    /** Paid, but after the due date. FR4.5 wants on-time distinguished from late. */
    val wasLate: Boolean
        get() = status == ContributionStatus.PAID && paidDate != null && paidDate > dueDate

    fun isOverdue(today: LocalDate): Boolean = isOutstanding && dueDate < today

    /** Negative once the due date has passed. */
    fun daysUntilDue(today: LocalDate): Long = ChronoUnit.DAYS.between(today, dueDate)

    /**
     * The fine now running, because the deadline passed with this unpaid (FR4.4).
     *
     * Derived from the dates rather than stored: `paidDate > dueDate` already records
     * whether a fine was incurred, so a second field would be a copy that could disagree
     * (ADR-0020).
     */
    fun fineIncurred(today: LocalDate): Money? =
        penalty?.takeIf { !it.isZero && isOverdue(today) }

    /** What settling it costs today — the contribution plus any fine already run up. */
    fun owedOn(today: LocalDate): Money = amount + (fineIncurred(today) ?: Money.ZERO)
}

/** A group with what it currently owes, for the group list and FR4.6. */
data class GroupSummary(
    val group: MoneyGroup,
    val outstanding: Money,
    val dueCount: Int,
    val nextDueDate: LocalDate?,
)
