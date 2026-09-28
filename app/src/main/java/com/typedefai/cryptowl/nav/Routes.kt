package com.typedefai.cryptowl.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.ui.graphics.vector.ImageVector
import com.typedefai.cryptowl.R

/** Navigation routes for the authenticated shell. */
object Routes {
    // Onboarding graph
    const val ONBOARDING_INTRO = "onboarding/intro"
    const val ONBOARDING_PASSWORD = "onboarding/password"
    const val ONBOARDING_BIOMETRIC = "onboarding/biometric"

    // Main shell (bottom navigation)
    const val MOMENTS = "moments"
    const val VAULT = "vault"
    const val ASSISTANT = "assistant"
    const val SETTINGS = "settings"

    // Full-screen destinations pushed above the shell
    const val LOCK = "lock"
    const val RESTORE = "restore"
    const val CHAT = "chat"

    // Vault hub drill-downs (placeholders until the features land)
    const val NOTES = "vault/notes"
    const val PASSWORDS = "vault/passwords"
    const val TOP_SECRET = "vault/top_secret"
    const val MEDIA = "vault/media"
}

/** A destination in the bottom navigation bar. */
enum class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    MOMENTS(
        route = Routes.MOMENTS,
        labelRes = R.string.nav_moments,
        icon = Icons.Outlined.PhotoLibrary,
        selectedIcon = Icons.Filled.PhotoLibrary,
    ),
    VAULT(
        route = Routes.VAULT,
        labelRes = R.string.nav_vault,
        icon = Icons.Outlined.Shield,
        selectedIcon = Icons.Filled.Shield,
    ),
    ASSISTANT(
        route = Routes.ASSISTANT,
        labelRes = R.string.nav_assistant,
        icon = Icons.Outlined.SmartToy,
        selectedIcon = Icons.Filled.SmartToy,
    ),
    SETTINGS(
        route = Routes.SETTINGS,
        labelRes = R.string.nav_settings,
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Filled.Settings,
    ),
}
