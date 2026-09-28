package com.typedefai.cryptowl.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * The Cryptowl theme: branded Material 3 color schemes (oxblood seed) plus
 * the app type scale. Dynamic color is deliberately not used — a vault should
 * look the same on every device.
 */
@Composable
fun CryptowlTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = CryptowlTypography,
        content = content,
    )
}
