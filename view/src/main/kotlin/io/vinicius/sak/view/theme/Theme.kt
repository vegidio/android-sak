package io.vinicius.sak.view.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// TODO: build the full dark color scheme from Color.kt tokens
private val SakDarkColorScheme = darkColorScheme(
    primary = SakPrimary,
    onPrimary = SakOnPrimary,
    primaryContainer = SakPrimaryContainer,
    onPrimaryContainer = SakOnPrimaryContainer,
    secondary = SakSecondary,
    onSecondary = SakOnSecondary,
    secondaryContainer = SakSecondaryContainer,
    onSecondaryContainer = SakOnSecondaryContainer,
    error = SakError,
    onError = SakOnError,
)

// TODO: build the full light color scheme from Color.kt tokens
private val SakLightColorScheme = lightColorScheme(
    primary = SakPrimary,
    onPrimary = SakOnPrimary,
    primaryContainer = SakPrimaryContainer,
    onPrimaryContainer = SakOnPrimaryContainer,
    secondary = SakSecondary,
    onSecondary = SakOnSecondary,
    secondaryContainer = SakSecondaryContainer,
    onSecondaryContainer = SakOnSecondaryContainer,
    error = SakError,
    onError = SakOnError,
    background = SakBackground,
    onBackground = SakOnBackground,
    surface = SakSurface,
    onSurface = SakOnSurface,
)

/**
 * SAK design-system theme wrapper.
 *
 * Wrap your screen or component tree with [SakTheme] to apply SAK colors,
 * typography, and shapes via [MaterialTheme].
 *
 * @param darkTheme Whether to use the dark color scheme. Defaults to the system setting.
 * @param content The composable content to theme.
 */
@Composable
fun SakTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) SakDarkColorScheme else SakLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SakTypography,
        content = content,
    )
}
