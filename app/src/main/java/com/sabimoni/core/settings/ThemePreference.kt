package com.sabimoni.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How the app decides between light and dark.
 *
 * [SYSTEM] is the default and stays the default: the phone already knows whether it is
 * night, and an app that ignores that setting is an app you have to manage twice.
 */
enum class ThemeChoice { SYSTEM, LIGHT, DARK }

@Singleton
class ThemePreference @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    /**
     * Falls back to [ThemeChoice.SYSTEM] for both an unset and an unreadable value, so a
     * preferences file written by a future version with a new choice cannot leave the app
     * unable to pick a theme at all.
     */
    fun observe(): Flow<ThemeChoice> = dataStore.data.map { preferences ->
        preferences[KEY]
            ?.let { stored -> ThemeChoice.entries.firstOrNull { it.name == stored } }
            ?: ThemeChoice.SYSTEM
    }

    suspend fun set(choice: ThemeChoice) {
        dataStore.edit { it[KEY] = choice.name }
    }

    private companion object {
        val KEY = stringPreferencesKey("theme_choice")
    }
}
