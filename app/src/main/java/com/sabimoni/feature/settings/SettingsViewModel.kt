package com.sabimoni.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabimoni.core.security.ApiKeyStore
import com.sabimoni.core.settings.ThemeChoice
import com.sabimoni.core.settings.ThemePreference
import com.sabimoni.core.sms.SmsCaptureGateway
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val hasApiKey: Boolean = false,
    val smsCaptureSupported: Boolean = false,
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val apiKeyStore: ApiKeyStore,
    private val themePreference: ThemePreference,
    smsCaptureGateway: SmsCaptureGateway,
) : ViewModel() {

    private val smsSupported = smsCaptureGateway.isSupported

    val uiState: StateFlow<SettingsUiState> = combine(
        apiKeyStore.observeHasKey(),
        themePreference.observe(),
    ) { hasKey, theme ->
        SettingsUiState(
            hasApiKey = hasKey,
            smsCaptureSupported = smsSupported,
            theme = theme,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(smsCaptureSupported = smsSupported),
        )

    fun setTheme(choice: ThemeChoice) {
        viewModelScope.launch { themePreference.set(choice) }
    }

    fun saveApiKey(key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { apiKeyStore.put(trimmed) }
    }

    fun clearApiKey() {
        viewModelScope.launch { apiKeyStore.clear() }
    }
}
