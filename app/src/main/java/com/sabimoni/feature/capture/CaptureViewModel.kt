package com.sabimoni.feature.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabimoni.core.data.model.CapturedMessage
import com.sabimoni.core.data.repository.CaptureRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CaptureUiState(
    val messages: List<CapturedMessage> = emptyList(),
    val pendingCount: Int = 0,
)

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val repository: CaptureRepository,
) : ViewModel() {

    val uiState: StateFlow<CaptureUiState> = combine(
        repository.observeThread(),
        repository.observePendingCount(),
    ) { messages, pendingCount ->
        CaptureUiState(messages = messages, pendingCount = pendingCount)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CaptureUiState(),
    )

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.capture(trimmed)
        }
    }
}
