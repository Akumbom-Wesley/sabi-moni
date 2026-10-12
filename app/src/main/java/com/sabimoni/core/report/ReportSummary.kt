package com.sabimoni.core.report

import com.sabimoni.core.data.model.LoggedEntry
import com.sabimoni.core.money.Money

/**
 * One row of a breakdown: a category or a group, and what it came to over the range.
 *
 * [id] is null for the uncategorised bucket — entries the parser could not place and the
 * user has not corrected. Shown rather than hidden, because money with no label is
 * exactly the thing a mirror should not quietly drop.
 */
data class ReportSlice(
    val id: Long?,
    val label: String,
    val amount: Money,
) {
    /** 0f..1f of [total], for a bar's width. Zero when there is nothing to divide by. */
    fun shareOf(total: Money): Float =
        if (total.xaf <= 0L) 0f else amount.xaf.toFloat() / total.xaf.toFloat()
}

/**
 * Everything the reports screen shows for one range (FR5.1).
 *
 * Deliberately carries no budget, cap or target. v1 reports are a mirror and nothing
 * else — see FR5.4 and design principle 3.
 */
data class ReportSummary(
    val range: DateRange,
    val income: Money,
    val expense: Money,
    val byCategory: List<ReportSlice>,
    val byGroup: List<ReportSlice>,
    /** Every entry in the range, newest first — the list FR5.3 drills down into. */
    val entries: List<LoggedEntry>,
) {
    val net: Money get() = income - expense

    val isEmpty: Boolean get() = entries.isEmpty()

    companion object {
        fun empty(range: DateRange) = ReportSummary(
            range = range,
            income = Money.ZERO,
            expense = Money.ZERO,
            byCategory = emptyList(),
            byGroup = emptyList(),
            entries = emptyList(),
        )
    }
}

/** Which slice the user tapped, and therefore which entries the drill-down lists. */
sealed interface ReportFocus {
    data object All : ReportFocus

    data class Category(val id: Long?, val label: String) : ReportFocus

    data class Group(val id: Long, val label: String) : ReportFocus
}

/**
 * The entries behind a figure (FR5.3).
 *
 * Filtered here rather than by a second query, so the list and the number above it are
 * two views of one read and cannot disagree about the same row.
 */
fun ReportSummary.entriesFor(focus: ReportFocus): List<LoggedEntry> = when (focus) {
    is ReportFocus.All -> entries
    is ReportFocus.Category -> entries.filter { it.categoryId == focus.id }
    is ReportFocus.Group -> entries.filter { it.groupId == focus.id }
}
