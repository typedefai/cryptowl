package com.typedefai.cryptowl.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.typedefai.cryptowl.R

/** Navigation routes for the authenticated shell. */
object Routes {
    // Onboarding graph
    const val ONBOARDING_INTRO = "onboarding/intro"
    const val ONBOARDING_PASSWORD = "onboarding/password"
    const val ONBOARDING_BIOMETRIC = "onboarding/biometric"

    // Main shell (bottom navigation)
    const val ITEMS = "vault/items"
    const val MEDIA = "vault/media"
    const val SETTINGS = "vault/settings"

    // Full-screen destinations pushed above the shell
    const val LOCK = "lock"
    const val RESTORE = "restore"

    // Item editor / history (null id = create)
    const val ITEM_EDITOR = "vault/items/edit?id={id}&type={type}"
    const val HISTORY = "vault/items/history/{id}"

    fun itemEditor(id: String? = null, type: String? = null): String {
        val params = buildList {
            id?.let { add("id=$it") }
            type?.let { add("type=$it") }
        }
        return if (params.isEmpty()) "vault/items/edit" else "vault/items/edit?${params.joinToString("&")}"
    }

    fun history(id: String): String = "vault/items/history/$id"
}

/** A destination in the bottom navigation bar. */
enum class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    ITEMS(
        route = Routes.ITEMS,
        labelRes = R.string.nav_items,
        icon = Icons.Outlined.Collections,
        selectedIcon = Icons.Filled.Collections,
    ),
    MEDIA(
        route = Routes.MEDIA,
        labelRes = R.string.nav_media,
        icon = Icons.Outlined.PhotoLibrary,
        selectedIcon = Icons.Filled.PhotoLibrary,
    ),
    SETTINGS(
        route = Routes.SETTINGS,
        labelRes = R.string.nav_settings,
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Filled.Settings,
    ),
}
