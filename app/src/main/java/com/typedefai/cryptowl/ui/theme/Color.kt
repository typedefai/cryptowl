package com.typedefai.cryptowl.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Cryptowl brand palette. The seed is the oxblood used by the splash/wordmark
 * (`0xFF8C2B1B`); the schemes below are the matching Material 3 tonal set.
 * Dynamic color is intentionally disabled so the vault keeps its identity.
 */

// Brand reference (also used outside the theme, e.g. the splash wordmark).
val BrandOxblood = Color(0xFF8C2B1B)

val LightColors = lightColorScheme(
    primary = Color(0xFF8C2B1B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD2),
    onPrimaryContainer = Color(0xFF3B0900),
    inversePrimary = Color(0xFFFFB4A1),
    secondary = Color(0xFF4A635C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8DF),
    onSecondaryContainer = Color(0xFF06201A),
    tertiary = Color(0xFF6C5D2F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF5E1A9),
    onTertiaryContainer = Color(0xFF231B00),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF201A18),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF201A18),
    surfaceVariant = Color(0xFFF4DED9),
    onSurfaceVariant = Color(0xFF53433F),
    outline = Color(0xFF85736E),
    outlineVariant = Color(0xFFD8C2BD),
    inverseSurface = Color(0xFF362F2D),
    inverseOnSurface = Color(0xFFFBEDEA),
    scrim = Color(0xFF000000),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB4A1),
    onPrimary = Color(0xFF561400),
    primaryContainer = Color(0xFF7A2E1F),
    onPrimaryContainer = Color(0xFFFFDAD2),
    inversePrimary = Color(0xFF8C2B1B),
    secondary = Color(0xFFB1CCC3),
    onSecondary = Color(0xFF1C3530),
    secondaryContainer = Color(0xFF334B46),
    onSecondaryContainer = Color(0xFFCCE8DF),
    tertiary = Color(0xFFD8C58E),
    onTertiary = Color(0xFF3B2F05),
    tertiaryContainer = Color(0xFF534618),
    onTertiaryContainer = Color(0xFFF5E1A9),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF201A18),
    onBackground = Color(0xFFEDE0DD),
    surface = Color(0xFF201A18),
    onSurface = Color(0xFFEDE0DD),
    surfaceVariant = Color(0xFF53433F),
    onSurfaceVariant = Color(0xFFD8C2BD),
    outline = Color(0xFFA08C87),
    outlineVariant = Color(0xFF53433F),
    inverseSurface = Color(0xFFEDE0DD),
    inverseOnSurface = Color(0xFF362F2D),
    scrim = Color(0xFF000000),
)
