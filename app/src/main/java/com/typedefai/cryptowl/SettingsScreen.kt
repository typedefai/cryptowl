package com.typedefai.cryptowl

import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.typedefai.cryptowl.crypto.ProtectedValue
import com.typedefai.cryptowl.settings.AutoLockPolicy
import com.typedefai.cryptowl.ui.components.SettingRow

/**
 * Settings: security (lock now, fingerprint unlock management), backup /
 * restore, language and basic vault info.
 */
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onLock: () -> Unit,
    onRestore: () -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity
    val biometricAvailable by viewModel.biometricUnlockAvailable.collectAsState()
    val biometricCipher by viewModel.biometricCipher.collectAsState()
    val biometricReady by viewModel.biometricReady.collectAsState()
    val biometricError by viewModel.biometricError.collectAsState()
    val backupProgress by viewModel.backupProgress.collectAsState()
    val backupError by viewModel.backupError.collectAsState()
    val passwordChangeBusy by viewModel.passwordChangeBusy.collectAsState()
    val passwordChangeError by viewModel.passwordChangeError.collectAsState()
    val passwordChanged by viewModel.passwordChanged.collectAsState()
    val autoLockTimeoutMs by viewModel.autoLockTimeoutMs.collectAsState()
    val flagSecure by viewModel.flagSecure.collectAsState()
    val vaultInfo by viewModel.vaultInfo.collectAsState()

    var showEnableDialog by rememberSaveable { mutableStateOf(false) }
    var showDisableDialog by rememberSaveable { mutableStateOf(false) }
    var showChangePasswordDialog by rememberSaveable { mutableStateOf(false) }
    var showAutoLockDialog by rememberSaveable { mutableStateOf(false) }
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    var showAboutDialog by rememberSaveable { mutableStateOf(false) }

    val pickBackupTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) viewModel.backupVault(uri)
    }

    val enableTitle = stringResource(R.string.settings_biometric_enable_title)
    val enableSubtitle = stringResource(R.string.settings_biometric_enable_subtitle)
    val cancelButton = stringResource(R.string.action_cancel)

    // The password dialog produced a wrap cipher: authorize it with the sensor.
    LaunchedEffect(biometricCipher, activity) {
        val cipher = biometricCipher ?: return@LaunchedEffect
        if (activity != null) {
            authenticateWithBiometric(
                context = context,
                activity = activity,
                cipher = cipher,
                title = enableTitle,
                subtitle = enableSubtitle,
                negativeButtonText = cancelButton,
                onAuthenticated = viewModel::completeBiometric,
                onCancelled = viewModel::cancelBiometricPrompt,
            )
        } else {
            viewModel.cancelBiometricPrompt()
        }
    }

    if (showEnableDialog) {
        EnableBiometricDialog(
            error = biometricError,
            onDismiss = {
                showEnableDialog = false
                viewModel.clearBiometricError()
            },
            onConfirm = { password ->
                showEnableDialog = false
                viewModel.enableBiometric(ProtectedValue.fromString(password))
            },
        )
    }
    if (showDisableDialog) {
        DisableBiometricDialog(
            onDismiss = { showDisableDialog = false },
            onConfirm = {
                showDisableDialog = false
                viewModel.disableBiometric()
            },
        )
    }
    if (showChangePasswordDialog) {
        ChangeMasterPasswordDialog(
            busy = passwordChangeBusy,
            error = passwordChangeError,
            changed = passwordChanged,
            onDismiss = {
                showChangePasswordDialog = false
                viewModel.clearPasswordChangeError()
                viewModel.consumePasswordChanged()
            },
            onConfirm = { current, newPassword -> viewModel.changeMasterPassword(current, newPassword) },
        )
    }
    if (showAutoLockDialog) {
        AutoLockDialog(
            current = autoLockTimeoutMs,
            onSelect = {
                viewModel.setAutoLockTimeout(it)
                showAutoLockDialog = false
            },
            onDismiss = { showAutoLockDialog = false },
        )
    }
    if (showLanguageDialog) {
        LanguageDialog(onDismiss = { showLanguageDialog = false })
    }
    if (showAboutDialog) {
        AboutDialog(
            info = vaultInfo,
            onDismiss = { showAboutDialog = false },
        )
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
                if (biometricAvailable) R.string.settings_biometric_on else R.string.settings_biometric_off,
            ),
            trailing = {
                Switch(
                    checked = biometricAvailable,
                    enabled = !biometricReady,
                    onCheckedChange = { wantEnabled ->
                        viewModel.clearBiometricError()
                        if (wantEnabled) showEnableDialog = true else showDisableDialog = true
                    },
                )
            },
            onClick = {
                viewModel.clearBiometricError()
                if (biometricAvailable) showDisableDialog = true else showEnableDialog = true
            },
        )
        biometricError?.takeIf { !showEnableDialog }?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        SettingRow(
            icon = Icons.Outlined.Key,
            title = stringResource(R.string.settings_change_password),
            subtitle = stringResource(R.string.settings_change_password_subtitle),
            onClick = {
                viewModel.clearPasswordChangeError()
                showChangePasswordDialog = true
            },
        )
        SettingRow(
            icon = Icons.Outlined.Timer,
            title = stringResource(R.string.settings_autolock),
            subtitle = autoLockLabel(autoLockTimeoutMs),
            onClick = { showAutoLockDialog = true },
        )
        SettingRow(
            icon = Icons.Filled.VisibilityOff,
            title = stringResource(R.string.settings_flag_secure),
            subtitle = stringResource(R.string.settings_flag_secure_subtitle),
            trailing = {
                Switch(
                    checked = flagSecure,
                    onCheckedChange = viewModel::setFlagSecure,
                )
            },
            onClick = { viewModel.setFlagSecure(!flagSecure) },
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
            onClick = { showLanguageDialog = true },
        )
        SettingRow(
            icon = Icons.Outlined.Info,
            title = stringResource(R.string.settings_about),
            subtitle = stringResource(R.string.settings_about_subtitle),
            onClick = {
                viewModel.loadVaultInfo()
                showAboutDialog = true
            },
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun EnableBiometricDialog(
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_biometric_enable_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.settings_biometric_enable_desc),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password_label)) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (password.isNotEmpty()) {
                        onConfirm(password)
                        password = ""
                    }
                },
                enabled = password.isNotEmpty(),
            ) {
                Text(stringResource(R.string.action_enable))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun DisableBiometricDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_biometric_disable_title)) },
        text = { Text(stringResource(R.string.settings_biometric_disable_desc)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.settings_biometric_disable_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun AboutDialog(info: String?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_about)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_about_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                info?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_cancel)) }
        },
    )
}

@StringRes
private fun autoLockLabelRes(timeoutMs: Long): Int = when (timeoutMs) {
    AutoLockPolicy.IMMEDIATE -> R.string.autolock_immediate
    AutoLockPolicy.ONE_MINUTE -> R.string.autolock_1m
    AutoLockPolicy.FIVE_MINUTES -> R.string.autolock_5m
    else -> R.string.autolock_30m
}

@Composable
private fun autoLockLabel(timeoutMs: Long): String = stringResource(autoLockLabelRes(timeoutMs))

@Composable
private fun AutoLockDialog(
    current: Long,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_autolock)) },
        text = {
            Column {
                AutoLockPolicy.options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = current == option, onClick = null)
                        Text(stringResource(autoLockLabelRes(option)))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
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

@Composable
private fun ChangeMasterPasswordDialog(
    busy: Boolean,
    error: String?,
    changed: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (current: String, newPassword: String) -> Unit,
) {
    var current by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var attempted by rememberSaveable { mutableStateOf(false) }

    val minLength = 8
    val tooShort = !BuildConfig.DEBUG && newPassword.length < minLength
    val mismatch = newPassword != confirm
    val canSubmit = current.isNotEmpty() && newPassword.isNotEmpty() && !tooShort && !mismatch && !busy

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.settings_change_password)) },
        text = {
            if (changed) {
                Text(stringResource(R.string.password_change_success))
            } else {
                Column {
                    OutlinedTextField(
                        value = current,
                        onValueChange = { current = it },
                        label = { Text(stringResource(R.string.password_current_label)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text(stringResource(R.string.password_new_label)) },
                        singleLine = true,
                        isError = attempted && (tooShort || mismatch),
                        supportingText = if (attempted && tooShort) {
                            { Text(stringResource(R.string.password_min_length, minLength)) }
                        } else if (attempted && mismatch) {
                            { Text(stringResource(R.string.password_mismatch)) }
                        } else {
                            null
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it },
                        label = { Text(stringResource(R.string.password_confirm_label)) },
                        singleLine = true,
                        isError = attempted && mismatch,
                        supportingText = if (attempted && mismatch) {
                            { Text(stringResource(R.string.password_mismatch)) }
                        } else {
                            null
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (busy) {
                        Spacer(Modifier.height(12.dp))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (changed) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_cancel)) }
            } else {
                TextButton(
                    onClick = {
                        attempted = true
                        if (canSubmit) onConfirm(current, newPassword)
                    },
                    enabled = canSubmit,
                ) {
                    Text(stringResource(R.string.passwords_save))
                }
            }
        },
        dismissButton = if (!changed) {
            { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.action_cancel)) } }
        } else {
            null
        },
    )
}
