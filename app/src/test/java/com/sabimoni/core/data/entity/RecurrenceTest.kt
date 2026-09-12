package com.sabimoni.core.data.entity

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class RecurrenceTest {

    private val sep11 = LocalDate.of(2026, 9, 11)

    // --- advancing one period -------------------------------------------------

    @Test
    fun advance_movesOnePeriodForEachUnit() {
        assertThat(RecurrenceUnit.WEEKLY.advance(sep11)).isEqualTo(LocalDate.of(2026, 9, 18))
        assertThat(RecurrenceUnit.FORTNIGHTLY.advance(sep11)).isEqualTo(LocalDate.of(2026, 9, 25))
        assertThat(RecurrenceUnit.MONTHLY.advance(sep11)).isEqualTo(LocalDate.of(2026, 10, 11))
        assertThat(RecurrenceUnit.QUARTERLY.advance(sep11)).isEqualTo(LocalDate.of(2026, 12, 11))
    }

    @Test
    fun advance_clampsToTheEndOfAShortMonthWithoutDrifting() {
        val jan31 = LocalDate.of(2026, 1, 31)

        val feb = RecurrenceUnit.MONTHLY.advance(jan31)
        assertThat(feb).isEqualTo(LocalDate.of(2026, 2, 28))

        // The anchor is what matters, not the clamped date: two periods on from the 31st is
        // the 31st of March, not the 28th of March. A schedule must not walk backwards.
        val mar = RecurrenceUnit.MONTHLY.advance(jan31, periods = 2)
        assertThat(mar).isEqualTo(LocalDate.of(2026, 3, 31))
    }

    @Test
    fun advance_handlesALeapYearEndOfMonth() {
        val jan31 = LocalDate.of(2028, 1, 31)

        assertThat(RecurrenceUnit.MONTHLY.advance(jan31)).isEqualTo(LocalDate.of(2028, 2, 29))
    }

    // --- generating deadlines -------------------------------------------------

    @Test
    fun deadlinesThrough_includesTheAnchorAndEveryPeriodReached() {
        val deadlines = deadlinesThrough(
            anchor = LocalDate.of(2026, 6, 26),
            unit = RecurrenceUnit.MONTHLY,
            through = LocalDate.of(2026, 9, 15),
        )

        assertThat(deadlines).containsExactly(
            LocalDate.of(2026, 6, 26),
            LocalDate.of(2026, 7, 26),
            LocalDate.of(2026, 8, 26),
        ).inOrder()
    }

    @Test
    fun deadlinesThrough_includesADeadlineFallingExactlyOnTheCutoff() {
        val deadlines = deadlinesThrough(
            anchor = LocalDate.of(2026, 8, 26),
            unit = RecurrenceUnit.MONTHLY,
            through = LocalDate.of(2026, 9, 26),
        )

        // The cutoff is today-plus-lead-time, so a deadline landing on it is exactly the
        // one the user needs warning about.
        assertThat(deadlines).contains(LocalDate.of(2026, 9, 26))
    }

    @Test
    fun deadlinesThrough_yieldsNothingBeforeTheAnchorArrives() {
        val deadlines = deadlinesThrough(
            anchor = LocalDate.of(2026, 10, 1),
            unit = RecurrenceUnit.MONTHLY,
            through = LocalDate.of(2026, 9, 15),
        )

        // No obligation exists yet, so none should be materialised.
        assertThat(deadlines).isEmpty()
    }

    @Test
    fun deadlinesThrough_keepsTheMostRecentWhenAScheduleHasLainDormant() {
        val deadlines = deadlinesThrough(
            anchor = LocalDate.of(2020, 1, 15),
            unit = RecurrenceUnit.MONTHLY,
            through = LocalDate.of(2026, 9, 15),
            limit = 3,
        )

        // The recent ones, not the oldest: catching up should mean "what you owe now",
        // not six years of resurrected history.
        assertThat(deadlines).containsExactly(
            LocalDate.of(2026, 7, 15),
            LocalDate.of(2026, 8, 15),
            LocalDate.of(2026, 9, 15),
        ).inOrder()
    }

    @Test
    fun deadlinesThrough_handlesWeeklyAndFortnightlyAcrossAMonthBoundary() {
        val weekly = deadlinesThrough(
            anchor = LocalDate.of(2026, 8, 28),
            unit = RecurrenceUnit.WEEKLY,
            through = LocalDate.of(2026, 9, 12),
        )
        assertThat(weekly).containsExactly(
            LocalDate.of(2026, 8, 28),
            LocalDate.of(2026, 9, 4),
            LocalDate.of(2026, 9, 11),
        ).inOrder()

        val fortnightly = deadlinesThrough(
            anchor = LocalDate.of(2026, 8, 28),
            unit = RecurrenceUnit.FORTNIGHTLY,
            through = LocalDate.of(2026, 9, 12),
        )
        assertThat(fortnightly).containsExactly(
            LocalDate.of(2026, 8, 28),
            LocalDate.of(2026, 9, 11),
        ).inOrder()
    }

    @Test
    fun deadlinesThrough_returnsJustTheAnchorWhenTheCutoffIsTheAnchor() {
        val deadlines = deadlinesThrough(
            anchor = sep11,
            unit = RecurrenceUnit.MONTHLY,
            through = sep11,
        )

        assertThat(deadlines).containsExactly(sep11)
    }
}
