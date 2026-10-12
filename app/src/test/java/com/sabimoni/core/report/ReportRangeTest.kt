package com.sabimoni.core.report

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class ReportRangeTest {

    /** A Wednesday, mid-month, mid-quarter — so no boundary is true by accident. */
    private val wed7Oct = LocalDate.of(2026, 10, 7)

    // --- the period containing a day ------------------------------------------

    @Test
    fun day_isJustThatDay() {
        val range = ReportPeriod.DAY.rangeAround(wed7Oct)

        assertThat(range).isEqualTo(DateRange(wed7Oct, wed7Oct))
        assertThat(range.isSingleDay).isTrue()
    }

    @Test
    fun week_runsMondayToSunday() {
        val range = ReportPeriod.WEEK.rangeAround(wed7Oct)

        assertThat(range.from).isEqualTo(LocalDate.of(2026, 10, 5))
        assertThat(range.to).isEqualTo(LocalDate.of(2026, 10, 11))
    }

    @Test
    fun month_coversTheWholeMonth() {
        val range = ReportPeriod.MONTH.rangeAround(wed7Oct)

        assertThat(range.from).isEqualTo(LocalDate.of(2026, 10, 1))
        assertThat(range.to).isEqualTo(LocalDate.of(2026, 10, 31))
    }

    @Test
    fun month_endsOnTheRealLastDayOfAShortMonth() {
        val feb = ReportPeriod.MONTH.rangeAround(LocalDate.of(2026, 2, 14))

        assertThat(feb.to).isEqualTo(LocalDate.of(2026, 2, 28))

        val leapFeb = ReportPeriod.MONTH.rangeAround(LocalDate.of(2028, 2, 14))

        assertThat(leapFeb.to).isEqualTo(LocalDate.of(2028, 2, 29))
    }

    @Test
    fun quarter_coversThreeMonths() {
        val range = ReportPeriod.QUARTER.rangeAround(wed7Oct)

        assertThat(range.from).isEqualTo(LocalDate.of(2026, 10, 1))
        assertThat(range.to).isEqualTo(LocalDate.of(2026, 12, 31))
    }

    @Test
    fun year_coversTheWholeYear() {
        val range = ReportPeriod.YEAR.rangeAround(wed7Oct)

        assertThat(range.from).isEqualTo(LocalDate.of(2026, 1, 1))
        assertThat(range.to).isEqualTo(LocalDate.of(2026, 12, 31))
    }

    // --- stepping between periods ---------------------------------------------

    @Test
    fun steppingBackAMonthLandsOnTheWholePreviousMonth() {
        val range = ReportPeriod.MONTH.rangeAround(wed7Oct, offset = -1)

        assertThat(range.from).isEqualTo(LocalDate.of(2026, 9, 1))
        assertThat(range.to).isEqualTo(LocalDate.of(2026, 9, 30))
    }

    @Test
    fun steppingBackFromJanuaryCrossesTheYear() {
        val range = ReportPeriod.MONTH.rangeAround(LocalDate.of(2026, 1, 15), offset = -1)

        assertThat(range.from).isEqualTo(LocalDate.of(2025, 12, 1))
        assertThat(range.to).isEqualTo(LocalDate.of(2025, 12, 31))
    }

    @Test
    fun steppingBackFromTheThirtyFirstStillCoversAShortMonth() {
        // The anchor's day-of-month must not leak into the range: stepping back from
        // 31 March is the whole of February, not a span starting on the 28th.
        val range = ReportPeriod.MONTH.rangeAround(LocalDate.of(2026, 3, 31), offset = -1)

        assertThat(range.from).isEqualTo(LocalDate.of(2026, 2, 1))
        assertThat(range.to).isEqualTo(LocalDate.of(2026, 2, 28))
    }

    @Test
    fun steppingQuartersMovesThreeMonths() {
        val range = ReportPeriod.QUARTER.rangeAround(wed7Oct, offset = -1)

        assertThat(range.from).isEqualTo(LocalDate.of(2026, 7, 1))
        assertThat(range.to).isEqualTo(LocalDate.of(2026, 9, 30))
    }

    @Test
    fun consecutivePeriodsTouchWithoutOverlappingOrLeavingAGap() {
        val september = ReportPeriod.MONTH.rangeAround(wed7Oct, offset = -1)
        val october = ReportPeriod.MONTH.rangeAround(wed7Oct)

        // Every day belongs to exactly one period, which is what stops a transaction
        // being counted twice or vanishing when the user steps between months.
        assertThat(september.to.plusDays(1)).isEqualTo(october.from)
    }

    // --- custom --------------------------------------------------------------

    @Test
    fun customIsNotSteppable() {
        assertThat(ReportPeriod.CUSTOM.isSteppable).isFalse()
        assertThat(ReportPeriod.MONTH.isSteppable).isTrue()
    }

    @Test
    fun aRangeCannotStartAfterItEnds() {
        runCatching { DateRange(wed7Oct, wed7Oct.minusDays(1)) }
            .onSuccess { error("expected an inverted range to be rejected") }
            .onFailure { assertThat(it).isInstanceOf(IllegalArgumentException::class.java) }
    }

    @Test
    fun containsIsInclusiveAtBothEnds() {
        val range = ReportPeriod.MONTH.rangeAround(wed7Oct)

        assertThat(LocalDate.of(2026, 10, 1) in range).isTrue()
        assertThat(LocalDate.of(2026, 10, 31) in range).isTrue()
        assertThat(LocalDate.of(2026, 9, 30) in range).isFalse()
        assertThat(LocalDate.of(2026, 11, 1) in range).isFalse()
    }
}
