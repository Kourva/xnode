package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class XNodeColors(
    val background: Color,
    val surface: Color,
    val surfaceCard: Color,
    val innerCard: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val border: Color,
    val isDark: Boolean
)

val LocalXNodeColors = staticCompositionLocalOf {
    XNodeColors(
        background = ExtraDarkBackground,
        surface = ExtraDarkSurface,
        surfaceCard = ExtraDarkSurfaceCard,
        innerCard = ExtraDarkInnerCard,
        textPrimary = ExtraDarkTextPrimary,
        textSecondary = ExtraDarkTextSecondary,
        border = ExtraDarkBorder,
        isDark = true
    )
}

object XNodeTheme {
    val colors: XNodeColors
        @Composable
        @ReadOnlyComposable
        get() = LocalXNodeColors.current
}

@Composable
fun XNodeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    extraDark: Boolean = true, // Extra Dark Mode enabled by default when in dark mode
    content: @Composable () -> Unit
) {
    val xNodeColors = when {
        darkTheme && extraDark -> XNodeColors(
            background = ExtraDarkBackground, // #0D0D0D pure black
            surface = ExtraDarkSurface,       // #1A1A1A settings boxes/surfaces
            surfaceCard = ExtraDarkSurfaceCard,
            innerCard = ExtraDarkInnerCard,
            textPrimary = ExtraDarkTextPrimary,
            textSecondary = ExtraDarkTextSecondary,
            border = ExtraDarkBorder,         // #2A2A2A border
            isDark = true
        )
        darkTheme -> XNodeColors(
            background = DarkModeBackground,   // #1A1A1A
            surface = DarkModeSurface,         // #0D0D0D
            surfaceCard = DarkModeSurfaceCard,
            innerCard = DarkModeInnerCard,
            textPrimary = DarkModeTextPrimary,
            textSecondary = DarkModeTextSecondary,
            border = DarkModeBorder,           // #2A2A2A
            isDark = true
        )
        else -> XNodeColors(
            background = LightBackground,      // #F5F5F5
            surface = LightSurface,            // #FFFFFF
            surfaceCard = LightSurfaceCard,
            innerCard = LightInnerCard,
            textPrimary = LightTextPrimary,    // #0D0D0D
            textSecondary = LightTextSecondary,
            border = LightBorder,              // #E0E0E0
            isDark = false
        )
    }

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            background = xNodeColors.background,
            surface = xNodeColors.surface,
            onBackground = xNodeColors.textPrimary,
            onSurface = xNodeColors.textPrimary,
            outline = xNodeColors.border,
            outlineVariant = xNodeColors.border
        )
    } else {
        lightColorScheme(
            background = xNodeColors.background,
            surface = xNodeColors.surface,
            onBackground = xNodeColors.textPrimary,
            onSurface = xNodeColors.textPrimary,
            outline = xNodeColors.border,
            outlineVariant = xNodeColors.border
        )
    }

    CompositionLocalProvider(LocalXNodeColors provides xNodeColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = XNodeTypography,
            content = content
        )
    }
}
