package com.typedefai.cryptowl

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.typedefai.cryptowl.ui.components.EmptyState
import com.typedefai.cryptowl.vault.NoteDraft
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Confidential-tier notes list (readable after vault unlock, no fingerprint). */
@Composable
fun NotesScreen(
    viewModel: NoteViewModel,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val entries by viewModel.entries.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(Unit) { viewModel.clearError() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.notes_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
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
                    icon = Icons.Outlined.Description,
                    title = stringResource(R.string.notes_empty),
                    hint = stringResource(R.string.notes_empty_hint),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(entries, key = { it.id }) { note ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = note.title.ifBlank { stringResource(R.string.notes_untitled) },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = { Text(formatNoteDate(note.updatedAt)) },
                            leadingContent = { Icon(Icons.Outlined.Description, contentDescription = null) },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (note.pinned) {
                                        Icon(
                                            Icons.Filled.Star,
                                            contentDescription = stringResource(R.string.notes_pinned),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                                }
                            },
                            modifier = Modifier.clickable { onOpen(note.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onAdd,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.notes_add))
        }
    }
}

/** Create/edit a note; supports a markdown preview toggle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditScreen(
    viewModel: NoteViewModel,
    id: String?,
    onBack: () -> Unit,
) {
    val detail by viewModel.detail.collectAsState()
    val saved by viewModel.saved.collectAsState()
    val error by viewModel.error.collectAsState()

    var title by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }
    var pinned by rememberSaveable { mutableStateOf(false) }
    var preview by rememberSaveable { mutableStateOf(false) }
    var prefilled by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id != null) viewModel.load(id)
    }
    LaunchedEffect(id, detail?.id) {
        val current = detail
        if (!prefilled && id != null && current?.id == id) {
            title = current.title
            content = current.content
            pinned = current.pinned
            prefilled = true
        }
    }
    LaunchedEffect(saved) {
        if (saved) {
            viewModel.consumeSaved()
            onBack()
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.notes_delete_title)) },
            text = { Text(stringResource(R.string.notes_delete_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    id?.let { viewModel.delete(it) }
                    onBack()
                }) {
                    Text(stringResource(R.string.notes_delete))
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
                title = {
                    Text(
                        if (id == null) stringResource(R.string.notes_new_title)
                        else stringResource(R.string.notes_edit_title),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back))
                    }
                },
                actions = {
                    if (id != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.notes_delete))
                        }
                    }
                    IconButton(onClick = { pinned = !pinned }) {
                        Icon(
                            imageVector = if (pinned) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = stringResource(
                                if (pinned) R.string.notes_unpin else R.string.notes_pin,
                            ),
                            tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = { preview = !preview }) {
                        Icon(
                            imageVector = if (preview) Icons.Filled.Edit else Icons.Filled.Visibility,
                            contentDescription = stringResource(
                                if (preview) R.string.notes_edit_content else R.string.notes_preview,
                            ),
                        )
                    }
                    TextButton(onClick = { viewModel.save(NoteDraft(id, title, content, pinned)) }) {
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
            if (preview) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = title.ifBlank { stringResource(R.string.notes_untitled) },
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(12.dp))
                MarkdownText(text = content)
                Spacer(Modifier.height(24.dp))
            } else {
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
                    minLines = 12,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.notes_markdown_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun formatNoteDate(epochMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMs))
