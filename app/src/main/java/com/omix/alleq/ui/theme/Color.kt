package com.omix.alleq.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class EqColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val border: Color,
    val borderSubtle: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val cardBg: Color,
    val cardBorder: Color,
    val sheetBg: Color,
    val sheetCardBg: Color,
    val faderTrack: Color,
    val faderLevelLine: Color,
    val badgeBg: Color
)

fun eqColorsFromColorScheme(colorScheme: ColorScheme, isDark: Boolean): EqColors {
    return EqColors(
        isDark = isDark,
        background = colorScheme.surface,
        surface = colorScheme.surfaceContainer,
        surfaceElevated = colorScheme.surfaceContainerHigh,
        border = colorScheme.outline,
        borderSubtle = colorScheme.outlineVariant,
        textPrimary = colorScheme.onSurface,
        textSecondary = colorScheme.onSurfaceVariant,
        textMuted = colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
        accent = colorScheme.primary,
        cardBg = colorScheme.surfaceContainerLow,
        cardBorder = colorScheme.outlineVariant.copy(alpha = 0.6f),
        sheetBg = colorScheme.surfaceContainerLow,
        sheetCardBg = colorScheme.surfaceContainer,
        faderTrack = colorScheme.surfaceContainerHighest,
        faderLevelLine = colorScheme.onSurface,
        badgeBg = colorScheme.surfaceContainerHigh
    )
}

val DefaultDarkColorScheme = darkColorScheme()
val DefaultLightColorScheme = lightColorScheme()

val DarkEqColors = eqColorsFromColorScheme(DefaultDarkColorScheme, true)
val LightEqColors = eqColorsFromColorScheme(DefaultLightColorScheme, false)

val LocalEqColors = staticCompositionLocalOf { DarkEqColors }
