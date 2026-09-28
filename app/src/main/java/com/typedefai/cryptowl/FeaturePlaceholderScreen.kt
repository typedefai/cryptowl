package com.typedefai.cryptowl

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.typedefai.cryptowl.ui.components.EmptyState

/**
 * Placeholder for a vault category whose feature has not landed yet (the
 * vault/tier schema is in place, the CRUD UI is not). Keeps the navigation
 * shell honest: routes exist, nothing silently dead-ends.
 */
@Composable
fun FeaturePlaceholderScreen(route: String) {
    val titleRes: Int = when (route) {
        com.typedefai.cryptowl.nav.Routes.NOTES -> R.string.vault_notes
        com.typedefai.cryptowl.nav.Routes.PASSWORDS -> R.string.vault_passwords
        com.typedefai.cryptowl.nav.Routes.TOP_SECRET -> R.string.vault_top_secret
        com.typedefai.cryptowl.nav.Routes.MEDIA -> R.string.vault_media
        else -> R.string.coming_soon
    }
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp),
        )
        Spacer(Modifier.height(8.dp))
        EmptyState(
            icon = Icons.Outlined.Construction,
            title = stringResource(R.string.coming_soon),
            hint = stringResource(R.string.coming_soon_hint),
            modifier = Modifier.weight(1f),
        )
    }
}
