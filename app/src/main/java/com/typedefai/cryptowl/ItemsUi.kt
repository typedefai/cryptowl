package com.typedefai.cryptowl

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.typedefai.cryptowl.model.Classification
import com.typedefai.cryptowl.model.ItemFilter
import com.typedefai.cryptowl.model.ItemSummary
import com.typedefai.cryptowl.model.ItemType
import com.typedefai.cryptowl.ui.components.EmptyState
import com.typedefai.cryptowl.ui.components.Tier
import com.typedefai.cryptowl.ui.components.TierBadge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The items list — type is an attribute, not a location (docs/items.md).
 * Filter chips switch between All / Plain / Password / Photo / Video; the
 * search box filters titles in place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemsScreen(
    viewModel: ItemsViewModel,
    onOpenItem: (id: String, type: String) -> Unit,
    onCreate: (String) -> Unit,
    onLock: () -> Unit,
) {
    val items by viewModel.items.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val query by viewModel.query.collectAsState()
    var showNewMenu by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ItemSummary?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.items_title)) },
                actions = {
                    IconButton(onClick = onLock) {
                        Icon(Icons.Outlined.Lock, contentDescription = stringResource(R.string.settings_lock_now))
                    }
                },
            )
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { showNewMenu = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.items_create))
                }
                DropdownMenu(expanded = showNewMenu, onDismissRequest = { showNewMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.item_new_note)) },
                        leadingIcon = { Icon(Icons.Outlined.Description, null) },
                        onClick = {
                            showNewMenu = false
                            onCreate(ItemType.PLAIN.id)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.item_new_password)) },
                        leadingIcon = { Icon(Icons.Outlined.Key, null) },
                        onClick = {
                            showNewMenu = false
                            onCreate(ItemType.PASSWORD.id)
                        },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                placeholder = { Text(stringResource(R.string.items_search_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(ItemFilter.entries.toList(), key = { it.name }) { entry ->
                    AssistChip(
                        onClick = { viewModel.setFilter(entry) },
                        label = { Text(stringResource(filterLabel(entry))) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (items.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Description,
                    title = stringResource(R.string.items_empty_title),
                    hint = stringResource(R.string.items_empty_hint),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items, key = { it.id }) { item ->
                        ItemRow(
                            item = item,
                            onClick = { onOpenItem(item.id, item.type) },
                            onDelete = { deleteTarget = item },
                        )
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.item_delete_title)) },
            text = { Text(stringResource(R.string.item_delete_desc, target.title)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(target.id)
                    deleteTarget = null
                }) { Text(stringResource(R.string.item_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun ItemRow(item: ItemSummary, onClick: () -> Unit, onDelete: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(item.title.ifBlank { stringResource(R.string.item_untitled) }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                text = stringResource(typeLabel(ItemType.fromId(item.type))) + " · " +
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(item.updatedAt)),
                style = MaterialTheme.typography.bodySmall,
            )
        },
        leadingContent = { Icon(typeIcon(ItemType.fromId(item.type)), contentDescription = null) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TierBadge(tierOf(item.classification))
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.item_delete))
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

@Composable
fun tierOf(classification: Classification): Tier = when (classification) {
    Classification.C -> Tier.CONFIDENTIAL
    Classification.S -> Tier.SECRET
    Classification.T -> Tier.TOP_SECRET
}

fun typeIcon(type: ItemType): ImageVector = when (type) {
    ItemType.PLAIN -> Icons.Outlined.Description
    ItemType.PASSWORD -> Icons.Outlined.Key
    ItemType.PHOTO -> Icons.Outlined.Image
    ItemType.VIDEO -> Icons.Outlined.Videocam
    ItemType.FOLDER -> Icons.Outlined.Folder
}

fun typeLabel(type: ItemType): Int = when (type) {
    ItemType.PLAIN -> R.string.type_plain
    ItemType.PASSWORD -> R.string.type_password
    ItemType.PHOTO -> R.string.type_photo
    ItemType.VIDEO -> R.string.type_video
    ItemType.FOLDER -> R.string.type_folder
}

fun filterLabel(filter: ItemFilter): Int = when (filter) {
    ItemFilter.ALL -> R.string.filter_all
    ItemFilter.PLAIN -> R.string.filter_plain
    ItemFilter.PASSWORD -> R.string.filter_password
    ItemFilter.PHOTO -> R.string.filter_photo
    ItemFilter.VIDEO -> R.string.filter_video
    ItemFilter.FOLDER -> R.string.type_folder
}
