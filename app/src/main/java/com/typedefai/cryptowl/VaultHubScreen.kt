package com.typedefai.cryptowl

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.typedefai.cryptowl.ui.components.Tier
import com.typedefai.cryptowl.ui.components.TierBadge

private data class VaultCategory(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val tier: Tier,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/**
 * The "Vault" tab: categories grouped by security tier. Confidential items are
 * readable once the vault is unlocked; Secret/Top-Secret items require
 * per-access fingerprint (and, for Top Secret, the secondary password).
 */
@Composable
fun VaultHubScreen(
    onOpenNotes: () -> Unit,
    onOpenPasswords: () -> Unit,
    onOpenTopSecret: () -> Unit,
    onOpenMedia: () -> Unit,
) {
    val categories = listOf(
        VaultCategory(
            titleRes = R.string.vault_notes,
            subtitleRes = R.string.vault_notes_subtitle,
            tier = Tier.CONFIDENTIAL,
            icon = Icons.Outlined.Description,
            onClick = onOpenNotes,
        ),
        VaultCategory(
            titleRes = R.string.vault_passwords,
            subtitleRes = R.string.vault_passwords_subtitle,
            tier = Tier.SECRET,
            icon = Icons.Outlined.Key,
            onClick = onOpenPasswords,
        ),
        VaultCategory(
            titleRes = R.string.vault_top_secret,
            subtitleRes = R.string.vault_top_secret_subtitle,
            tier = Tier.TOP_SECRET,
            icon = Icons.Outlined.Lock,
            onClick = onOpenTopSecret,
        ),
        VaultCategory(
            titleRes = R.string.vault_media,
            subtitleRes = R.string.vault_media_subtitle,
            tier = Tier.CONFIDENTIAL,
            icon = Icons.Outlined.PhotoLibrary,
            onClick = onOpenMedia,
        ),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = stringResource(R.string.vault_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp),
        )
        Text(
            text = stringResource(R.string.vault_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(16.dp))
        categories.forEach { category ->
            VaultCategoryRow(category)
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun VaultCategoryRow(category: VaultCategory) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = category.onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = category.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(category.titleRes), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(category.subtitleRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TierBadge(category.tier)
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
        )
    }
}
