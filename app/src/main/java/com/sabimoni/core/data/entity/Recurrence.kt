package com.sabimoni.core.data.entity

import java.time.LocalDate

/**
 * How often a group expects a contribution.
 *
 * Like every converter-backed enum, these names are the stored representation — renaming
 * one is a data migration (ADR-0027).
 */
enum class RecurrenceUnit { WEEKLY, FORTNIGHTLY, MONTHLY, QUARTERLY }

/**
 * The deadline one period on from [from].
 *
 * `plusMonths` clamps to the end of a short month, which is the behaviour we want: a
 * schedule anchored on the 31st falls due on the 28th in February and returns to the 31st
 * in March, rather than drifting earlier every month.
 */
fun RecurrenceUnit.advance(from: LocalDate, periods: Long = 1): LocalDate = when (this) {
    RecurrenceUnit.WEEKLY -> from.plusWeeks(periods)
    RecurrenceUnit.FORTNIGHTLY -> from.plusWeeks(2 * periods)
    RecurrenceUnit.MONTHLY -> from.plusMonths(periods)
    RecurrenceUnit.QUARTERLY -> from.plusMonths(3 * periods)
}

/**
 * Every deadline this schedule has reached, from [anchor] up to and including [through].
 *
 * `through` is normally *today plus the reminder lead time*, so the upcoming deadline is
 * materialised early enough to be warned about — the whole point of configuring a
 * recurring contribution once (ADR-0029).
 *
 * An anchor still in the future yields nothing: there is no obligation yet. A long-dormant
 * schedule yields at most [limit] deadlines, and the **most recent** ones — resurrecting
 * two years of missed church contributions on first launch would be technically honest and
 * practically useless.
 */
fun deadlinesThrough(
    anchor: LocalDate,
    unit: RecurrenceUnit,
    through: LocalDate,
    limit: Int = DEFAULT_DEADLINE_LIMIT,
): List<LocalDate> {
    if (anchor > through) return emptyList()

    val deadlines = mutableListOf<LocalDate>()
    var next = anchor
    // Bounded by `through`, and by a hard ceiling so a nonsense anchor cannot spin here.
    while (next <= through && deadlines.size < MAX_ITERATIONS) {
        deadlines += next
        next = unit.advance(next)
    }

    return deadlines.takeLast(limit)
}

/** Two years of monthly periods — more than enough to catch up, few enough to stay sane. */
private const val DEFAULT_DEADLINE_LIMIT = 24

/** A guard against a wildly old anchor, not a product rule. */
private const val MAX_ITERATIONS = 2_000
