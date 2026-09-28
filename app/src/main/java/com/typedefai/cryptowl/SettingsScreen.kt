package com.typedefai.cryptowl

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.typedefai.cryptowl.ui.components.SettingRow
import com.typedefai.cryptowl.ui.components.StatusChip

/**
 * Settings: security (lock now, biometric availability), backup/restore,
 * language, and basic vault info. Destructive/high-risk actions stay here
 * rather than on the lock screen.
 */
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onLock: () -> Unit,
    onRestore: () -> Unit,
) {
    val biometricAvailable by viewModel.biometricUnlockAvailable.collectAsState()
    val backupProgress by viewModel.backupProgress.collectAsState()
    val backupError by viewModel.backupError.collectAsState()

    val pickBackupTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) viewModel.backupVault(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = stringResource(R.string.nav_settings),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
        )

        SectionHeader(stringResource(R.string.settings_section_security))
        SettingRow(
            icon = Icons.Outlined.Lock,
            title = stringResource(R.string.settings_lock_now),
            subtitle = stringResource(R.string.settings_lock_now_subtitle),
            onClick = onLock,
        )
        SettingRow(
            icon = Icons.Outlined.Fingerprint,
            title = stringResource(R.string.settings_biometric),
            subtitle = stringResource(
                if (biometricAvailable) R.string.settings_biometric_on else R.string.settings_biometric_off
            ),
            trailing = {
                StatusChip(
                    stringResource(
                        if (biometricAvailable) R.string.settings_status_on else R.string.settings_status_off
                    )
                )
            },
            onClick = null,
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SectionHeader(stringResource(R.string.settings_section_data))
        SettingRow(
            icon = Icons.Outlined.Backup,
            title = stringResource(R.string.home_backup),
            subtitle = backupProgress?.let { stringResource(R.string.settings_backup_running) }
                ?: stringResource(R.string.settings_backup_subtitle),
            onClick = { pickBackupTree.launch(null) },
        )
        SettingRow(
            icon = Icons.Outlined.Restore,
            title = stringResource(R.string.home_restore),
            subtitle = stringResource(R.string.settings_restore_subtitle),
            onClick = onRestore,
        )
        backupError?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SectionHeader(stringResource(R.string.settings_section_general))
        SettingRow(
            icon = Icons.Outlined.Language,
            title = stringResource(R.string.language_option),
            subtitle = stringResource(R.string.settings_language_subtitle),
            onClick = null,
        )
        SettingRow(
            icon = Icons.Outlined.Info,
            title = stringResource(R.string.settings_about),
            subtitle = stringResource(R.string.settings_about_subtitle),
            onClick = null,
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
    )
}
