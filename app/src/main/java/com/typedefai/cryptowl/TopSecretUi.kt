package com.typedefai.cryptowl

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.typedefai.cryptowl.crypto.ProtectedValue
import com.typedefai.cryptowl.ui.components.EmptyState
import com.typedefai.cryptowl.vault.NoteDraft
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The two-step L3 gate: a fingerprint prompt (VM cipher) followed by the
 * secondary-password dialog. Rendered by every Top-Secret screen.
 */
@Composable
private fun TopSecretGate(viewModel: TopSecretViewModel) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity
    val cipher by viewModel.pendingCipher.collectAsState()
    val awaitingPassword by viewModel.awaitingPassword.collectAsState()
    val error by viewModel.error.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val promptTitle = stringResource(R.string.top_secret_prompt_title)
    val promptSubtitle = stringResource(R.string.top_secret_prompt_subtitle)
    val cancel = stringResource(R.string.action_cancel)

    LaunchedEffect(cipher, activity) {
        val pending = cipher ?: return@LaunchedEffect
        if (activity != null) {
            authenticateWithBiometric(
                context = context,
                activity = activity,
                cipher = pending,
                title = promptTitle,
                subtitle = promptSubtitle,
                negativeButtonText = cancel,
                onAuthenticated = viewModel::completeFingerprint,
                onCancelled = viewModel::cancelAccess,
            )
        } else {
            viewModel.cancelAccess()
        }
    }

    if (awaitingPassword) {
        SecondaryPasswordDialog(
            error = error,
            busy = busy,
            onDismiss = viewModel::cancelAccess,
            onConfirm = viewModel::submitSecondaryPassword,
        )
    }
}

@Composable
private fun SecondaryPasswordDialog(
    error: String?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.top_secret_verify_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.top_secret_verify_desc),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.top_secret_password_label)) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (busy) {
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (password.isNotEmpty()) onConfirm(password) },
                enabled = password.isNotEmpty() && !busy,
            ) {
                Text(stringResource(R.string.passwords_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Top-Secret list: setup CTA, then encrypted notes gated per access. */
@Composable
fun TopSecretScreen(
    viewModel: TopSecretViewModel,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val entries by viewModel.entries.collectAsState()
    val configured by viewModel.configured.collectAsState()
    val error by viewModel.error.collectAsState()
    var showSetupDialog by rememberSaveable { mutableStateOf(false) }

    TopSecretGate(viewModel)
    LaunchedEffect(Unit) { viewModel.clearError() }

    if (showSetupDialog) {
        SetupSecondaryPasswordDialog(
            error = error,
            onDismiss = {
                showSetupDialog = false
                viewModel.clearError()
            },
            onConfirm = {
                showSetupDialog = false
                viewModel.setup(ProtectedValue.fromString(it))
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Text(
                text = stringResource(R.string.vault_top_secret),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
            )
            error?.takeIf { !showSetupDialog }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            when {
                configured == false -> EmptyState(
                    icon = Icons.Outlined.Lock,
                    title = stringResource(R.string.top_secret_setup),
                    hint = stringResource(R.string.top_secret_setup_desc),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                entries.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.Lock,
                    title = stringResource(R.string.top_secret_empty),
                    hint = stringResource(R.string.top_secret_empty_hint),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                else -> LazyColumn(modifier = Modifier.weight(1f)) {
                    items(entries, key = { it.id }) { entry ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = entry.title.ifBlank { stringResource(R.string.notes_untitled) },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = { Text(formatTopSecretDate(entry.updatedAt)) },
                            leadingContent = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                            trailingContent = {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                            },
                            modifier = Modifier.clickable { onOpen(entry.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }

        if (configured == false) {
            Button(
                onClick = { showSetupDialog = true },
                modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp),
            ) {
                Text(stringResource(R.string.top_secret_setup))
            }
        } else if (configured == true) {
            FloatingActionButton(
                onClick = onAdd,
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.top_secret_add))
            }
        }
    }
}

@Composable
private fun SetupSecondaryPasswordDialog(
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val mismatch = password != confirm
    val canSubmit = password.isNotEmpty() && !mismatch

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.top_secret_setup)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.top_secret_setup_desc),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.top_secret_password_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    attempted = true
                    if (canSubmit) onConfirm(password)
                },
                enabled = canSubmit,
            ) {
                Text(stringResource(R.string.action_enable))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Read one Top-Secret note (fingerprint + secondary password first). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopSecretDetailScreen(
    viewModel: TopSecretViewModel,
    id: String,
    onEdit: () -> Unit,
    onBack: () -> Unit,
) {
    val detail by viewModel.detail.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val error by viewModel.error.collectAsState()
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }

    TopSecretGate(viewModel)
    LaunchedEffect(id) {
        if (detail?.id != id) viewModel.reveal(id)
    }
    LaunchedEffect(detail, busy, deleting) {
        if (deleting && detail == null && !busy) onBack()
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.notes_delete_title)) },
            text = { Text(stringResource(R.string.notes_delete_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    deleting = true
                    viewModel.delete(id)
                }) {
                    Text(stringResource(R.string.notes_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.title ?: stringResource(R.string.vault_top_secret)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back))
                    }
                },
                actions = {
                    IconButton(onClick = onEdit, enabled = detail != null && !busy) {
                        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.notes_edit_title))
                    }
                    IconButton(onClick = { showDeleteDialog = true }, enabled = detail != null && !busy) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.notes_delete))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val current = detail
            when {
                current != null -> Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                ) {
                    MarkdownText(text = current.content)
                    Spacer(Modifier.height(24.dp))
                }
                error != null -> Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = { viewModel.reveal(id) }) { Text(stringResource(R.string.passwords_retry)) }
                }
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

/** Create/edit a Top-Secret note; saving re-runs the two-factor gate. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopSecretEditScreen(
    viewModel: TopSecretViewModel,
    id: String?,
    onBack: () -> Unit,
) {
    val detail by viewModel.detail.collectAsState()
    val saved by viewModel.saved.collectAsState()
    val error by viewModel.error.collectAsState()
    var title by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }
    var prefilled by rememberSaveable { mutableStateOf(false) }

    TopSecretGate(viewModel)
    LaunchedEffect(id) {
        if (id != null && detail?.id != id) viewModel.reveal(id)
    }
    LaunchedEffect(id, detail?.id) {
        val current = detail
        if (!prefilled && id != null && current?.id == id) {
            title = current.title
            content = current.content
            prefilled = true
        }
    }
    LaunchedEffect(saved) {
        if (saved) {
            viewModel.consumeSaved()
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (id == null) stringResource(R.string.top_secret_new_title)
                        else stringResource(R.string.top_secret_edit_title),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back))
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.save(NoteDraft(id, title, content)) }) {
                        Text(stringResource(R.string.passwords_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.notes_field_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text(stringResource(R.string.notes_field_content)) },
                minLines = 10,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun formatTopSecretDate(epochMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMs))
