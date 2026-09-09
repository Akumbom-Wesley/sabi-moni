package com.sabimoni.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Green40,
    onPrimary = Color.White,
    primaryContainer = Green90,
    onPrimaryContainer = Green40,
    secondary = Sand40,
    secondaryContainer = Sand80,
    error = Clay40,
    background = Neutral99,
    surface = Neutral99,
    onBackground = Neutral10,
    onSurface = Neutral10,
)

private val DarkColors = darkColorScheme(
    primary = Green80,
    onPrimary = Green40,
    primaryContainer = Green40,
    onPrimaryContainer = Green90,
    secondary = Sand80,
    secondaryContainer = Sand40,
    error = Clay80,
    background = Neutral10,
    surface = Neutral20,
    onBackground = Neutral95,
    onSurface = Neutral95,
)

@Composable
fun SabiMoniTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SabiMoniTypography,
        content = content,
    )
}
