package com.sabimoni.core.report

import java.time.LocalDate
import java.time.temporal.IsoFields
import java.time.temporal.WeekFields

/**
 * The spans a report can cover (FR5.2).
 *
 * `CUSTOM` is deliberately part of the same enum rather than a separate concept: every
 * period resolves to a pair of dates, and the only thing custom does differently is take
 * those dates from the user instead of deriving them.
 */
enum class ReportPeriod { DAY, WEEK, MONTH, QUARTER, YEAR, CUSTOM }

/**
 * An inclusive span of days. Inclusive at both ends because that is what SQL `BETWEEN`
 * does, and having the type disagree with the query it feeds would be a bug waiting to
 * be written.
 */
data class DateRange(val from: LocalDate, val to: LocalDate) {
    init {
        require(from <= to) { "range starts after it ends: $from .. $to" }
    }

    operator fun contains(date: LocalDate): Boolean = date in from..to

    val isSingleDay: Boolean get() = from == to
}

/**
 * The period containing [anchor], shifted by [offset] periods.
 *
 * A negative offset steps back, which is how the screen moves between months without
 * holding a separate "which month" state: the period and an integer are enough to name
 * any span, so going back is arithmetic rather than bookkeeping.
 *
 * Weeks start on Monday regardless of locale. The alternative — `WeekFields.of(locale)` —
 * would mean the same week showing different totals on a phone set to a different region,
 * and a personal ledger that changes its mind about what a week is cannot be a mirror.
 */
fun ReportPeriod.rangeAround(anchor: LocalDate, offset: Int = 0): DateRange = when (this) {
    ReportPeriod.DAY -> anchor.plusDays(offset.toLong()).let { DateRange(it, it) }

    ReportPeriod.WEEK -> {
        val start = anchor.plusWeeks(offset.toLong())
            .with(WeekFields.ISO.dayOfWeek(), 1L)
        DateRange(start, start.plusDays(6))
    }

    ReportPeriod.MONTH -> {
        val start = anchor.plusMonths(offset.toLong()).withDayOfMonth(1)
        DateRange(start, start.plusMonths(1).minusDays(1))
    }

    ReportPeriod.QUARTER -> {
        val start = anchor.plusMonths(3L * offset)
            .with(IsoFields.DAY_OF_QUARTER, 1L)
        DateRange(start, start.plusMonths(3).minusDays(1))
    }

    ReportPeriod.YEAR -> {
        val start = anchor.plusYears(offset.toLong()).withDayOfYear(1)
        DateRange(start, start.plusYears(1).minusDays(1))
    }

    // A custom span is whatever the user picked; stepping it has no meaning, so the
    // caller keeps the dates and this never shifts them.
    ReportPeriod.CUSTOM -> DateRange(anchor, anchor)
}

/** Whether stepping between periods makes sense — false only for a hand-picked span. */
val ReportPeriod.isSteppable: Boolean get() = this != ReportPeriod.CUSTOM
