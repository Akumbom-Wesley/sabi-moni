package com.sabimoni.core.data.repository

import com.sabimoni.core.data.dao.CategoryTotal
import com.sabimoni.core.data.dao.GroupTotal
import com.sabimoni.core.data.dao.TransactionDao
import com.sabimoni.core.data.dao.TransactionRow
import com.sabimoni.core.data.entity.Direction
import com.sabimoni.core.money.Money
import com.sabimoni.core.report.DateRange
import com.sabimoni.core.report.ReportSlice
import com.sabimoni.core.report.ReportSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The retrospective mirror (FR5.x). Read-only by design: nothing here writes, because a
 * report that could change the ledger it reports on would stop being a mirror.
 */
@Singleton
class ReportRepository @Inject constructor(
    private val transactionDao: TransactionDao,
) {

    /**
     * Everything one range's report needs, as a single flow.
     *
     * The two totals and both breakdowns are aggregated in SQL (ADR-0005) rather than
     * folded from the row list, so a year's report stays a handful of numbers crossing
     * the boundary instead of a year of rows summed twice.
     */
    fun observeSummary(range: DateRange): Flow<ReportSummary> = combine(
        transactionDao.observeTotal(Direction.INCOME, range.from, range.to),
        transactionDao.observeTotal(Direction.EXPENSE, range.from, range.to),
        transactionDao.observeSpendByCategory(range.from, range.to),
        transactionDao.observeSpendByGroup(range.from, range.to),
        transactionDao.observeRowsInRange(range.from, range.to),
    ) { income, expense, categories, groups, rows ->
        ReportSummary(
            range = range,
            income = Money(income),
            expense = Money(expense),
            byCategory = categories.map(CategoryTotal::toSlice),
            byGroup = groups.map(GroupTotal::toSlice),
            entries = rows.map(TransactionRow::toDomain),
        )
    }
}

/**
 * An entry the parser could not place keeps a slice of its own rather than being folded
 * into a catch-all or dropped. "Uncategorised" being the biggest bar is a useful thing to
 * be told.
 */
private fun CategoryTotal.toSlice() = ReportSlice(
    id = categoryId,
    label = categoryName ?: "Uncategorised",
    amount = Money(totalXaf),
)

private fun GroupTotal.toSlice() = ReportSlice(
    id = groupId,
    label = groupName,
    amount = Money(totalXaf),
)
