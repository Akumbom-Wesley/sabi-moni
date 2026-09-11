package com.sabimoni.feature.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabimoni.core.data.model.Category
import com.sabimoni.core.data.model.DayTotals
import com.sabimoni.core.data.model.ThreadItem
import com.sabimoni.core.data.repository.CaptureRepository
import com.sabimoni.core.data.repository.TransactionRepository
import com.sabimoni.core.money.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class CaptureUiState(
    /** The day the running total covers — see [CaptureViewModel.uiState] on when it moves. */
    val today: LocalDate,
    /** Income minus expense over everything logged. The headline figure. */
    val balance: Money = Money.ZERO,
    val items: List<ThreadItem> = emptyList(),
    val pendingCount: Int = 0,
    val totals: DayTotals = DayTotals.EMPTY,
    val categories: List<Category> = emptyList(),
)

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val capture: CaptureRepository,
    private val transactions: TransactionRepository,
    private val clock: Clock,
) : ViewModel() {

    /**
     * "Today" is resolved once per subscription rather than once per ViewModel, so
     * reopening the app after midnight shows the new day's total. It deliberately does not
     * roll over while the screen is open: someone finishing the nightly ritual at 23:59
     * would otherwise watch the entries they just logged drop out of the total mid-task.
     * See docs/adr/0018-corrections-and-manual-entry.md.
     */
    val uiState: StateFlow<CaptureUiState> = flow {
        val today = LocalDate.now(clock)
        emitAll(
            combine(
                capture.observeThread(),
                capture.observePendingCount(),
                transactions.observeDayTotals(today),
                transactions.observeCategories(),
                transactions.observeBalance(),
            ) { items, pendingCount, totals, categories, balance ->
                CaptureUiState(
                    today = today,
                    balance = balance,
                    items = items,
                    pendingCount = pendingCount,
                    totals = totals,
                    categories = categories,
                )
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CaptureUiState(today = LocalDate.now(clock)),
    )

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            capture.capture(trimmed)
        }
    }

    /** FR1.6 — form entry, for when a tap is faster than a sentence. */
    fun addEntry(edit: EntryEdit) {
        viewModelScope.launch {
            transactions.addManualEntry(
                amount = edit.amount,
                direction = edit.direction,
                categoryId = edit.categoryId,
                note = edit.note,
                occurredOn = edit.occurredOn,
            )
        }
    }

    /** FR1.4 — a correction on one line, with the message left exactly as it was sent. */
    fun correctEntry(id: Long, edit: EntryEdit) {
        viewModelScope.launch {
            transactions.correctEntry(
                id = id,
                amount = edit.amount,
                direction = edit.direction,
                categoryId = edit.categoryId,
                note = edit.note,
                occurredOn = edit.occurredOn,
            )
        }
    }

    /** Deletes a whole selection at once, so the running total moves once (ADR-0019). */
    fun deleteEntries(ids: Set<Long>) {
        viewModelScope.launch {
            transactions.deleteEntries(ids)
        }
    }

    /** Re-queues a message the parser gave up on (ADR-0017 left this without a home). */
    fun retry(messageId: Long) {
        viewModelScope.launch {
            capture.retry(messageId)
        }
    }
}
