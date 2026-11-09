package com.typedefai.cryptowl

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.typedefai.cryptowl.model.Classification
import com.typedefai.cryptowl.model.ItemType

/**
 * Item editor for all content types. S/T payloads stay locked until the
 * per-access gate runs: BiometricPrompt for S (plus a one-time master-password
 * import when the vault came from the desktop), fingerprint + secondary
 * password for T.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEditorScreen(
    viewModel: ItemEditorViewModel,
    itemId: String?,
    typeId: String?,
    onBack: () -> Unit,
    onHistory: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity
    var showDelete by remember { mutableStateOf(false) }
    var revealSecret by remember { mutableStateOf(false) }

    LaunchedEffect(itemId, typeId) { viewModel.open(itemId, typeId) }

    val promptTitle = stringResource(R.string.biometric_prompt_title)
    val promptSubtitle = stringResource(R.string.biometric_prompt_subtitle)
    val cancelLabel = stringResource(R.string.action_cancel)

    LaunchedEffect(state.pendingCipher, activity) {
        val cipher = state.pendingCipher ?: return@LaunchedEffect
        if (activity == null) return@LaunchedEffect
        if (state.awaitingMasterPassword) return@LaunchedEffect
        authenticateWithBiometric(
            context = context,
            activity = activity,
            cipher = cipher,
            title = promptTitle,
            subtitle = promptSubtitle,
            negativeButtonText = cancelLabel,
            onAuthenticated = viewModel::onBiometricAuthorized,
            onCancelled = viewModel::onBiometricCancelled,
        )
    }

    LaunchedEffect(state.saved, state.deleted) {
        if (state.saved || state.deleted) {
            viewModel.clearKeys()
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(editorTitle(state)) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.clearKeys(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (state.exists) {
                        IconButton(onClick = { onHistory(state.id!!) }) {
                            Icon(Icons.Outlined.History, contentDescription = stringResource(R.string.editor_history))
                        }
                        IconButton(onClick = { showDelete = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.item_delete))
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (state.exists || !state.locked) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = viewModel::save,
                        enabled = !state.busy && (!state.locked || viewModel.isUnlocked()),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.editor_save)) }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.exists && state.locked && !viewModel.isUnlocked()) {
                LockedPanel(
                    classification = state.classification,
                    busy = state.busy,
                    onUnlock = viewModel::requestUnlock,
                )
            } else {
                val type = ItemType.fromId(state.type)
                OutlinedTextField(
                    value = state.title,
                    onValueChange = viewModel::updateTitle,
                    label = { Text(stringResource(R.string.editor_field_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (type != ItemType.PHOTO && type != ItemType.VIDEO && type != ItemType.FOLDER) {
                    TierSelector(
                        selected = state.classification,
                        allowTopSecret = true,
                        onChange = viewModel::updateClassification,
                    )
                }
                when (type) {
                    ItemType.PASSWORD -> {
                        OutlinedTextField(
                            value = state.username,
                            onValueChange = viewModel::updateUsername,
                            label = { Text(stringResource(R.string.editor_field_username)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = state.url,
                            onValueChange = viewModel::updateUrl,
                            label = { Text(stringResource(R.string.editor_field_url)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = state.secret,
                            onValueChange = viewModel::updateSecret,
                            label = { Text(stringResource(R.string.editor_field_secret)) },
                            singleLine = true,
                            visualTransformation = if (revealSecret) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                            trailingIcon = {
                                IconButton(onClick = { revealSecret = !revealSecret }) {
                                    Icon(
                                        if (revealSecret) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = stringResource(
                                            if (revealSecret) R.string.password_hide else R.string.password_show,
                                        ),
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = state.notes,
                            onValueChange = viewModel::updateNotes,
                            label = { Text(stringResource(R.string.editor_field_notes)) },
                            minLines = 4,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    ItemType.PLAIN -> {
                        OutlinedTextField(
                            value = state.content,
                            onValueChange = viewModel::updateContent,
                            label = { Text(stringResource(R.string.editor_field_content)) },
                            minLines = 12,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    else -> {
                        Text(
                            text = stringResource(R.string.editor_media_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.item_delete_title)) },
            text = { Text(stringResource(R.string.item_delete_desc, state.title)) },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    viewModel.delete()
                }) { Text(stringResource(R.string.item_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (state.awaitingMasterPassword) {
        MasterPasswordDialog(
            onDismiss = viewModel::cancelGate,
            onConfirm = viewModel::submitMasterPassword,
        )
    }
    if (state.awaitingSecondaryPassword) {
        SecondaryPasswordDialog(
            onDismiss = viewModel::cancelGate,
            onConfirm = viewModel::submitSecondaryPassword,
        )
    }
}

@Composable
private fun LockedPanel(
    classification: Classification,
    busy: Boolean,
    onUnlock: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Outlined.Lock,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Text(
            text = if (classification == Classification.T) {
                stringResource(R.string.editor_locked_top_secret)
            } else {
                stringResource(R.string.editor_locked_secret)
            },
            style = MaterialTheme.typography.titleMedium,
        )
        OutlinedButton(onClick = onUnlock, enabled = !busy) {
            Text(stringResource(R.string.editor_unlock))
        }
    }
}

@Composable
private fun TierSelector(
    selected: Classification,
    allowTopSecret: Boolean,
    onChange: (Classification) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Classification.entries.forEach { tier ->
            if (tier == Classification.T && !allowTopSecret) return@forEach
            FilterChip(
                selected = selected == tier,
                onClick = { onChange(tier) },
                label = {
                    Text(
                        when (tier) {
                            Classification.C -> stringResource(R.string.editor_tier_c)
                            Classification.S -> stringResource(R.string.editor_tier_s)
                            Classification.T -> stringResource(R.string.editor_tier_t)
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun editorTitle(state: ItemEditorViewModel.UiState): String {
    if (!state.exists) {
        return when (ItemType.fromId(state.type)) {
            ItemType.PASSWORD -> stringResource(R.string.editor_new_password)
            ItemType.PHOTO -> stringResource(R.string.type_photo)
            ItemType.VIDEO -> stringResource(R.string.type_video)
            else -> stringResource(R.string.editor_new_note)
        }
    }
    return stringResource(R.string.editor_edit_title)
}

@Composable
fun MasterPasswordDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_master_password_title)) },
        text = {
            Column {
                Text(stringResource(R.string.editor_master_password_desc))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text(stringResource(R.string.password_label)) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = password.isNotEmpty()) {
                Text(stringResource(R.string.editor_unlock))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun SecondaryPasswordDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.top_secret_verify_title)) },
        text = {
            Column {
                Text(stringResource(R.string.top_secret_verify_desc))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text(stringResource(R.string.top_secret_password_label)) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = password.isNotEmpty()) {
                Text(stringResource(R.string.editor_unlock))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
