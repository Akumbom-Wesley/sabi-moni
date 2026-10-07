package com.sabimoni.feature.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabimoni.core.data.model.LoggedEntry
import com.sabimoni.core.data.repository.ReportRepository
import com.sabimoni.core.report.DateRange
import com.sabimoni.core.report.ReportFocus
import com.sabimoni.core.report.ReportPeriod
import com.sabimoni.core.report.ReportSummary
import com.sabimoni.core.report.entriesFor
import com.sabimoni.core.report.isSteppable
import com.sabimoni.core.report.rangeAround
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class ReportsUiState(
    val period: ReportPeriod,
    val offset: Int,
    val focus: ReportFocus,
    val summary: ReportSummary,
) {
    val range: DateRange get() = summary.range

    /** The entries under whatever is focused — the whole range until a slice is tapped. */
    val visibleEntries: List<LoggedEntry> get() = summary.entriesFor(focus)

    val canStep: Boolean get() = period.isSteppable

    val isFocused: Boolean get() = focus !is ReportFocus.All
}

/**
 * What the user has chosen to look at. Kept apart from the data so that changing the
 * range cancels the previous query rather than racing it.
 */
private data class Selection(
    val period: ReportPeriod = ReportPeriod.MONTH,
    val offset: Int = 0,
    val custom: DateRange? = null,
    val focus: ReportFocus = ReportFocus.All,
) {
    fun rangeOn(today: LocalDate): DateRange = when (period) {
        ReportPeriod.CUSTOM -> custom ?: ReportPeriod.MONTH.rangeAround(today)
        else -> period.rangeAround(today, offset)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val repository: ReportRepository,
    clock: Clock,
) : ViewModel() {

    /**
     * Resolved once, when the screen's model is created, rather than read per emission.
     * Every range is relative to it, so letting it advance would silently move "this
     * month" under someone still reading it.
     */
    private val today: LocalDate = LocalDate.now(clock)

    private val selection = MutableStateFlow(Selection())

    val uiState: StateFlow<ReportsUiState> = selection
        .flatMapLatest { choice ->
            repository.observeSummary(choice.rangeOn(today)).map { summary ->
                ReportsUiState(
                    period = choice.period,
                    offset = choice.offset,
                    focus = choice.focus,
                    summary = summary,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ReportsUiState(
                period = Selection().period,
                offset = 0,
                focus = ReportFocus.All,
                summary = ReportSummary.empty(Selection().rangeOn(today)),
            ),
        )

    /**
     * Switching span returns to the current period and drops any drill-down: the slice
     * you were looking at in March is not meaningfully "the same" slice in the year view.
     */
    fun selectPeriod(period: ReportPeriod) {
        selection.update { Selection(period = period, custom = it.custom) }
    }

    /** Moves a whole period back (-1) or forward (+1). FR5.2's ranges are navigable. */
    fun step(delta: Int) {
        selection.update {
            if (!it.period.isSteppable) it else it.copy(offset = it.offset + delta, focus = ReportFocus.All)
        }
    }

    fun setCustomRange(from: LocalDate, to: LocalDate) {
        val range = if (from <= to) DateRange(from, to) else DateRange(to, from)
        selection.update {
            Selection(period = ReportPeriod.CUSTOM, custom = range)
        }
    }

    /** Drill-down (FR5.3): tapping a figure lists the entries behind it. */
    fun focusOn(focus: ReportFocus) {
        selection.update { it.copy(focus = focus) }
    }

    fun clearFocus() {
        selection.update { it.copy(focus = ReportFocus.All) }
    }
}
