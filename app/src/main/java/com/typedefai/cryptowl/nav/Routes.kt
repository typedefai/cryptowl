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

    // Password CRUD
    const val PASSWORD_DETAIL = "vault/passwords/detail/{id}"
    const val PASSWORD_EDIT = "vault/passwords/edit?id={id}"

    fun passwordDetail(id: String): String = "vault/passwords/detail/$id"

    fun passwordEdit(id: String? = null): String =
        if (id == null) "vault/passwords/edit" else "vault/passwords/edit?id=$id"

    // Notes CRUD (Confidential tier)
    const val NOTE_EDIT = "vault/notes/edit?id={id}"

    fun noteEdit(id: String? = null): String =
        if (id == null) "vault/notes/edit" else "vault/notes/edit?id=$id"

    // Top-Secret notes (per-access two-factor gate)
    const val TOP_SECRET_DETAIL = "vault/top_secret/detail/{id}"
    const val TOP_SECRET_EDIT = "vault/top_secret/edit?id={id}"

    fun topSecretDetail(id: String): String = "vault/top_secret/detail/$id"

    fun topSecretEdit(id: String? = null): String =
        if (id == null) "vault/top_secret/edit" else "vault/top_secret/edit?id=$id"
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
