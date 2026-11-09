package com.typedefai.cryptowl

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.typedefai.cryptowl.ui.components.EmptyState
import com.typedefai.cryptowl.ui.components.TierBadge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Version history: immutable snapshots per item (docs/items.md "History").
 * Selecting a version diffs it against the current payload; restoring writes
 * the snapshot as a new version instead of rewriting history.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    itemId: String,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity
    var restoreTarget by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(itemId) { viewModel.load(itemId) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { viewModel.clearKeys() }
    }

    val promptTitle = stringResource(R.string.biometric_prompt_title)
    val promptSubtitle = stringResource(R.string.biometric_prompt_subtitle)
    val cancelLabel = stringResource(R.string.action_cancel)

    LaunchedEffect(state.pendingCipher, activity) {
        val cipher = state.pendingCipher ?: return@LaunchedEffect
        if (activity == null || state.awaitingMasterPassword) return@LaunchedEffect
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

    LaunchedEffect(state.restored) {
        if (state.restored) {
            viewModel.consumeRestored()
            viewModel.load(itemId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                text = state.itemTitle,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            state.error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (state.versions.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.History,
                    title = stringResource(R.string.history_empty),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.versions, key = { it.seq }) { version ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    stringResource(
                                        R.string.history_version,
                                        version.seq,
                                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                            .format(Date(version.createdAt)),
                                    ),
                                )
                            },
                            supportingContent = {
                                Text(version.title.ifBlank { stringResource(R.string.item_untitled) })
                            },
                            leadingContent = { TierBadge(tierOf(version.classification)) },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = {
                                        restoreTarget = version.seq
                                    }) {
                                        Icon(Icons.Outlined.Restore, contentDescription = stringResource(R.string.history_restore))
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectVersion(version.seq) },
                        )
                        HorizontalDivider()
                    }
                    if (state.diff.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.history_diff_title),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                        items(state.diff.size) { index ->
                            val line = state.diff[index]
                            val color = when (line.kind) {
                                DiffLineKind.EQUAL -> MaterialTheme.colorScheme.surface
                                DiffLineKind.ADDED -> MaterialTheme.colorScheme.tertiaryContainer
                                DiffLineKind.REMOVED -> MaterialTheme.colorScheme.errorContainer
                            }
                            val prefix = when (line.kind) {
                                DiffLineKind.EQUAL -> " "
                                DiffLineKind.ADDED -> "+"
                                DiffLineKind.REMOVED -> "-"
                            }
                            Text(
                                text = prefix + line.text,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(color)
                                    .padding(horizontal = 16.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    restoreTarget?.let { seq ->
        AlertDialog(
            onDismissRequest = { restoreTarget = null },
            title = { Text(stringResource(R.string.history_restore)) },
            text = { Text(stringResource(R.string.history_restore_desc, seq)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.restore(seq)
                    restoreTarget = null
                }) { Text(stringResource(R.string.history_restore)) }
            },
            dismissButton = {
                TextButton(onClick = { restoreTarget = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (state.awaitingMasterPassword) {
        MasterPasswordDialog(onDismiss = { viewModel.clearError() }, onConfirm = viewModel::submitMasterPassword)
    }
    if (state.awaitingSecondaryPassword) {
        SecondaryPasswordDialog(onDismiss = { viewModel.clearError() }, onConfirm = viewModel::submitSecondaryPassword)
    }
}
