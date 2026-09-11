package com.sabimoni

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabimoni.core.settings.ThemeChoice
import com.sabimoni.core.settings.ThemePreference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Holds the one piece of state that has to exist above the whole app: which theme to
 * paint it in.
 *
 * Scoped to the activity rather than a screen, and separate from `SettingsViewModel`,
 * because the theme outlives whichever tab is open — Settings writes the preference, this
 * reads it, and the DataStore flow is what connects them.
 *
 * `SharingStarted.Eagerly`, unlike every other state flow in the app: a five-second
 * timeout would be a chance to repaint the whole app in the wrong theme, and this is one
 * boolean.
 */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    themePreference: ThemePreference,
) : ViewModel() {

    val choice: StateFlow<ThemeChoice> = themePreference.observe().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ThemeChoice.SYSTEM,
    )
}
