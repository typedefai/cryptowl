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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.typedefai.cryptowl.ui.components.EmptyState
import com.typedefai.cryptowl.vault.PasswordDraft
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shared biometric gate: observes the ViewModel's pending cipher and shows the
 * system prompt. Used by every password screen so an access is never silently
 * performed without authentication.
 */
@Composable
private fun PasswordBiometricGate(viewModel: PasswordViewModel) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity
    val cipher by viewModel.pendingCipher.collectAsState()
    val title = stringResource(R.string.passwords_prompt_title)
    val subtitle = stringResource(R.string.passwords_prompt_subtitle)
    val cancel = stringResource(R.string.action_cancel)

    LaunchedEffect(cipher, activity) {
        val pending = cipher ?: return@LaunchedEffect
        if (activity != null) {
            authenticateWithBiometric(
                context = context,
                activity = activity,
                cipher = pending,
                title = title,
                subtitle = subtitle,
                negativeButtonText = cancel,
                onAuthenticated = viewModel::completeBiometric,
                onCancelled = viewModel::cancelBiometric,
            )
        } else {
            viewModel.cancelBiometric()
        }
    }
}

// ------------------------------------------------------------------- list

/** Secret-tier password list: titles only (no fingerprint needed to browse). */
@Composable
fun PasswordsScreen(
    viewModel: PasswordViewModel,
    biometricEnabled: Boolean,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val entries by viewModel.entries.collectAsState()
    val error by viewModel.error.collectAsState()
    PasswordBiometricGate(viewModel)

    LaunchedEffect(Unit) { viewModel.clearError() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Text(
                text = stringResource(R.string.passwords_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
            )

            if (!biometricEnabled) {
                Text(
                    text = stringResource(R.string.passwords_requires_fingerprint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            if (entries.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Key,
                    title = stringResource(R.string.passwords_empty),
                    hint = stringResource(R.string.passwords_empty_hint),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(entries, key = { it.id }) { entry ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = entry.title.ifBlank { stringResource(R.string.passwords_untitled) },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = { Text(formatDate(entry.updatedAt)) },
                            leadingContent = { Icon(Icons.Outlined.Key, contentDescription = null) },
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

        if (biometricEnabled) {
            FloatingActionButton(
                onClick = onAdd,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.passwords_add))
            }
        }
    }
}

// ----------------------------------------------------------------- detail

/** One entry; opening it requires a fresh fingerprint (L2 per-access rule). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordDetailScreen(
    viewModel: PasswordViewModel,
    id: String,
    onEdit: () -> Unit,
    onBack: () -> Unit,
) {
    val detail by viewModel.detail.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val error by viewModel.error.collectAsState()
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var revealed by rememberSaveable { mutableStateOf(false) }

    PasswordBiometricGate(viewModel)

    LaunchedEffect(id) {
        if (detail?.id != id) viewModel.reveal(id)
    }

    // Delete completed (detail cleared, not busy anymore): leave the screen.
    LaunchedEffect(detail, busy, deleting) {
        if (deleting && detail == null && !busy) onBack()
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.passwords_delete_title)) },
            text = { Text(stringResource(R.string.passwords_delete_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    deleting = true
                    viewModel.delete(id)
                }) {
                    Text(stringResource(R.string.passwords_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.title ?: stringResource(R.string.passwords_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back))
                    }
                },
                actions = {
                    IconButton(onClick = onEdit, enabled = detail != null && !busy) {
                        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.passwords_edit_title))
                    }
                    IconButton(onClick = { showDeleteDialog = true }, enabled = detail != null && !busy) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.passwords_delete))
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val current = detail
            when {
                current != null -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                ) {
                    FieldRow(stringResource(R.string.passwords_field_username), current.username)
                    FieldRow(stringResource(R.string.passwords_field_password), current.password, secret = true, revealed = revealed, onToggleReveal = { revealed = !revealed })
                    FieldRow(stringResource(R.string.passwords_field_url), current.url)
                    FieldRow(stringResource(R.string.passwords_field_notes), current.notes)
                    Spacer(Modifier.height(24.dp))
                }
                error != null -> Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = { viewModel.reveal(id) }) {
                        Text(stringResource(R.string.passwords_retry))
                    }
                }
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Fingerprint, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.passwords_unlocking), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldRow(
    label: String,
    value: String,
    secret: Boolean = false,
    revealed: Boolean = false,
    onToggleReveal: (() -> Unit)? = null,
) {
    if (value.isBlank()) return
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (secret && !revealed) "••••••••••••" else value,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = if (secret) FontFamily.Monospace else FontFamily.Default,
                modifier = Modifier.weight(1f),
            )
            if (secret && onToggleReveal != null) {
                IconButton(onClick = onToggleReveal) {
                    Icon(
                        imageVector = if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = stringResource(
                            if (revealed) R.string.passwords_hide else R.string.passwords_show,
                        ),
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------- edit

/** Create or edit an entry; saving requires a fingerprint (KEK access). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordEditScreen(
    viewModel: PasswordViewModel,
    id: String?,
    onBack: () -> Unit,
) {
    val detail by viewModel.detail.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val saved by viewModel.saved.collectAsState()
    val error by viewModel.error.collectAsState()

    var title by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var prefilled by rememberSaveable { mutableStateOf(false) }

    PasswordBiometricGate(viewModel)

    // Editing: load the decrypted values (already revealed on the detail screen).
    LaunchedEffect(id) {
        if (id != null && detail?.id != id) viewModel.reveal(id)
    }
    LaunchedEffect(id, detail?.id) {
        val current = detail
        if (!prefilled && id != null && current?.id == id) {
            title = current.title
            username = current.username
            password = current.password
            url = current.url
            notes = current.notes
            prefilled = true
        }
    }
    LaunchedEffect(saved) {
        if (saved) {
            viewModel.consumeSaved()
            onBack()
        }
    }

    val titleValid = title.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (id == null) stringResource(R.string.passwords_new_title)
                        else stringResource(R.string.passwords_edit_title),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            attempted = true
                            if (titleValid) {
                                viewModel.save(PasswordDraft(id, title, username, password, url, notes))
                            }
                        },
                        enabled = !busy,
                    ) {
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
                label = { Text(stringResource(R.string.passwords_field_title)) },
                singleLine = true,
                isError = attempted && !titleValid,
                supportingText = if (attempted && !titleValid) {
                    { Text(stringResource(R.string.passwords_title_required)) }
                } else {
                    null
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(stringResource(R.string.passwords_field_username)) },
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.passwords_field_password)) },
                singleLine = true,
                visualTransformation = if (passwordVisible) {
                    androidx.compose.ui.text.input.VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringResource(
                                if (passwordVisible) R.string.passwords_hide else R.string.passwords_show,
                            ),
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.passwords_field_url)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.passwords_field_notes)) },
                minLines = 3,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
            if (busy) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun formatDate(epochMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMs))
