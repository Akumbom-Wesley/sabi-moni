package com.sabimoni.feature.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabimoni.core.data.model.MoneyGroup
import com.sabimoni.core.data.repository.GroupRepository
import com.sabimoni.core.money.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupsUiState(
    val groups: List<MoneyGroup> = emptyList(),
    val totalOutstanding: Money = Money.ZERO,
)

@HiltViewModel
class GroupsViewModel @Inject constructor(
    private val repository: GroupRepository,
) : ViewModel() {

    val uiState: StateFlow<GroupsUiState> = combine(
        repository.observeGroups(),
        repository.observeTotalOutstanding(),
    ) { groups, outstanding ->
        GroupsUiState(groups = groups, totalOutstanding = outstanding)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GroupsUiState(),
    )

    fun createGroup(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.createGroup(name = trimmed)
        }
    }
}
