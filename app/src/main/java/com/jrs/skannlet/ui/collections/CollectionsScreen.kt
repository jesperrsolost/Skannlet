package com.jrs.skannlet.ui.collections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jrs.skannlet.R
import com.jrs.skannlet.app.CollectionListItemUiState
import com.jrs.skannlet.app.CollectionsUiState
import com.jrs.skannlet.app.UserUiState
import com.jrs.skannlet.ui.components.AppHeader
import com.jrs.skannlet.ui.components.UserPickerDialog
import com.jrs.skannlet.util.formatDateTime

@Composable
fun CollectionsRoute(
    uiState: CollectionsUiState,
    activeUserName: String?,
    users: List<UserUiState>,
    onCreateCollection: (String, Boolean) -> Unit,
    onOpenCollection: (String) -> Unit,
    onSetActiveCollection: (String) -> Unit,
    onUnlockCollection: (String) -> Unit,
    onDeleteCollection: (String) -> Unit,
    onSetActiveUser: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var showUserPicker by rememberSaveable { mutableStateOf(false) }
    var deleteCollectionId by rememberSaveable { mutableStateOf<String?>(null) }
    val deleteCollection = uiState.items.firstOrNull { it.id == deleteCollectionId }

    CollectionsScreen(
        uiState = uiState,
        activeUserName = activeUserName,
        onCreateClick = { showCreateDialog = true },
        onChangeUserClick = { showUserPicker = true },
        onOpenCollection = onOpenCollection,
        onSetActiveCollection = onSetActiveCollection,
        onUnlockCollection = onUnlockCollection,
        onDeleteCollection = { collectionId -> deleteCollectionId = collectionId },
        modifier = modifier,
    )

    if (showCreateDialog) {
        CreateCollectionDialog(
            onConfirm = { name, isReturn ->
                showCreateDialog = false
                onCreateCollection(name, isReturn)
            },
            onDismiss = { showCreateDialog = false },
        )
    }

    if (showUserPicker) {
        UserPickerDialog(
            users = users,
            onSelectUser = { userId ->
                showUserPicker = false
                onSetActiveUser(userId)
            },
            onDismiss = { showUserPicker = false },
        )
    }

    if (deleteCollectionId != null) {
        AlertDialog(
            onDismissRequest = { deleteCollectionId = null },
            title = { Text("Slett prosjekt") },
            text = {
                Text(
                    "Prosjektet \"${deleteCollection?.name ?: "valgt prosjekt"}\" og alle scannede rader slettes lokalt.",
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    onClick = {
                        val collectionId = deleteCollectionId ?: return@Button
                        deleteCollectionId = null
                        onDeleteCollection(collectionId)
                    },
                ) {
                    Text("Slett")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCollectionId = null }) {
                    Text("Avbryt")
                }
            },
        )
    }
}

@Composable
private fun CreateCollectionDialog(
    onConfirm: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var isReturn by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nytt prosjekt") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Navn på prosjekt") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = isReturn,
                            role = Role.Checkbox,
                            onValueChange = { isReturn = it },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = isReturn,
                        onCheckedChange = null,
                    )
                    Text("Merk som retur")
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.trim().isNotEmpty(),
                onClick = { onConfirm(name.trim(), isReturn) },
            ) {
                Text("Opprett")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Avbryt")
            }
        },
    )
}

@Composable
fun CollectionsScreen(
    uiState: CollectionsUiState,
    activeUserName: String?,
    onCreateClick: () -> Unit,
    onChangeUserClick: () -> Unit,
    onOpenCollection: (String) -> Unit,
    onSetActiveCollection: (String) -> Unit,
    onUnlockCollection: (String) -> Unit,
    onDeleteCollection: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val normalizedSearchQuery = searchQuery.trim()
    val filteredItems = remember(uiState.items, normalizedSearchQuery) {
        if (normalizedSearchQuery.isBlank()) {
            uiState.items
        } else {
            uiState.items.filter { item ->
                item.name.contains(normalizedSearchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppHeader(
            title = "Prosjekter",
            activeUserName = activeUserName,
            onChangeUserClick = onChangeUserClick,
            modifier = Modifier.fillMaxWidth(),
        )

        if (uiState.items.isEmpty()) {
            EmptyCollections(onCreateClick = onCreateClick)
        } else {
            CollectionSearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
            )
            CreateCollectionButton(
                text = "Nytt prosjekt",
                onClick = onCreateClick,
                modifier = Modifier.fillMaxWidth(),
            )

            if (filteredItems.isEmpty()) {
                EmptySearchResults(
                    query = normalizedSearchQuery,
                    onClearSearch = { searchQuery = "" },
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        CollectionListItem(
                            item = item,
                            onOpen = { onOpenCollection(item.id) },
                            onSetActive = { onSetActiveCollection(item.id) },
                            onUnlock = { onUnlockCollection(item.id) },
                            onDelete = { onDeleteCollection(item.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        label = { Text("Søk i prosjekter") },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun EmptyCollections(
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Ingen prosjekter ennå",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Opprett et prosjekt før scanning.",
                style = MaterialTheme.typography.bodyMedium,
            )
            CreateCollectionButton(
                text = "Opprett prosjekt",
                onClick = onCreateClick,
            )
        }
    }
}

@Composable
private fun CreateCollectionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
    ) {
        Icon(
            painter = painterResource(R.drawable.add_24px),
            contentDescription = null,
        )
        Text(
            text = text,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun EmptySearchResults(
    query: String,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Ingen prosjekter funnet",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Ingen prosjekter matcher \"$query\".",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onClearSearch) {
                Text("Tøm søk")
            }
        }
    }
}

@Composable
private fun CollectionListItem(
    item: CollectionListItemUiState,
    onOpen: () -> Unit,
    onSetActive: () -> Unit,
    onUnlock: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (item.isReturn) ReturnTag()
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        text = "${item.scanCount} skanninger",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Sist endret: ${formatDateTime(item.updatedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (item.isActive) {
                        AssistChip(
                            onClick = {},
                            label = { Text("Aktiv") },
                        )
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            painter = painterResource(R.drawable.more_vert_24px),
                            contentDescription = "Flere valg",
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        if (item.isLocked) {
                            DropdownMenuItem(
                                text = { Text("Lås opp prosjekt") },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.lock_open_right_24px),
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onUnlock()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Slett prosjekt") },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.delete_24px),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpen) {
                    Text("Åpne")
                }
                if (item.isLocked) {
                    OutlinedButton(
                        enabled = false,
                        onClick = {},
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.lock_24px),
                            contentDescription = "Prosjekt låst",
                        )
                    }
                } else {
                    OutlinedButton(
                        enabled = !item.isActive,
                        onClick = onSetActive,
                    ) {
                        Text("Sett aktiv")
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CollectionsScreenPreview() {
    CollectionsScreen(
        uiState = CollectionsUiState(
            items = listOf(
                CollectionListItemUiState(
                    id = "1",
                    projectNumber = 1,
                    name = "Varetelling juni",
                    isReturn = true,
                    scanCount = 3,
                    updatedAt = System.currentTimeMillis(),
                    isActive = true,
                    isLocked = false,
                ),
            ),
            activeCollectionId = "1",
        ),
        activeUserName = "Ola Nordmann",
        onCreateClick = {},
        onChangeUserClick = {},
        onOpenCollection = {},
        onSetActiveCollection = {},
        onUnlockCollection = {},
        onDeleteCollection = {},
    )
}
