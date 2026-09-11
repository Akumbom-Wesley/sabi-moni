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

/**
 * The `surfaceContainer*` roles are set explicitly, not left to Material's defaults: the
 * capture screen layers a thread background, cards and a header on top of each other, and
 * the baseline containers are purple-cast greys that clash with the green brand ramp.
 */
private val LightColors = lightColorScheme(
    primary = Green40,
    onPrimary = Color.White,
    primaryContainer = Green90,
    onPrimaryContainer = Green40,
    secondary = Sand40,
    secondaryContainer = Sand80,
    onSecondaryContainer = Neutral10,
    error = Clay40,
    background = Neutral97,
    onBackground = Neutral10,
    surface = Neutral99,
    onSurface = Neutral10,
    surfaceVariant = Neutral90,
    onSurfaceVariant = Neutral40,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Neutral97,
    surfaceContainer = Neutral95,
    surfaceContainerHigh = Neutral92,
    surfaceContainerHighest = Neutral90,
    outline = Neutral60,
    outlineVariant = Neutral80,
)

private val DarkColors = darkColorScheme(
    primary = Green80,
    onPrimary = Green40,
    primaryContainer = Green40,
    onPrimaryContainer = Green90,
    secondary = Sand80,
    secondaryContainer = Sand40,
    onSecondaryContainer = Neutral95,
    error = Clay80,
    background = Neutral10,
    onBackground = Neutral95,
    surface = Neutral12,
    onSurface = Neutral95,
    surfaceVariant = Neutral30,
    onSurfaceVariant = Neutral80,
    surfaceContainerLowest = Neutral10,
    surfaceContainerLow = Neutral12,
    surfaceContainer = Neutral17,
    surfaceContainerHigh = Neutral20,
    surfaceContainerHighest = Neutral24,
    outline = Neutral60,
    outlineVariant = Neutral30,
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
