package com.sabimoni

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabimoni.core.settings.ThemeChoice
import com.sabimoni.ui.SabiMoniApp
import com.sabimoni.ui.theme.SabiMoniTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Read here rather than inside SabiMoniTheme so ui/theme stays a pure
            // presentation layer with no knowledge of where a preference is stored.
            val viewModel: ThemeViewModel = hiltViewModel()
            val choice by viewModel.choice.collectAsStateWithLifecycle()

            val darkTheme = when (choice) {
                ThemeChoice.SYSTEM -> isSystemInDarkTheme()
                ThemeChoice.LIGHT -> false
                ThemeChoice.DARK -> true
            }

            // `enableEdgeToEdge()` decides status-bar icon contrast once, from the system
            // dark-mode setting — not from the theme this app chose. Forcing Light on a
            // phone in dark mode therefore left white icons on a white header, which is
            // the clock and battery disappearing. Re-applied here on every theme change,
            // driven by the theme actually being painted. See ADR-0024.
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !darkTheme
                        isAppearanceLightNavigationBars = !darkTheme
                    }
                }
            }

            SabiMoniTheme(darkTheme = darkTheme) {
                SabiMoniApp()
            }
        }
    }
}
