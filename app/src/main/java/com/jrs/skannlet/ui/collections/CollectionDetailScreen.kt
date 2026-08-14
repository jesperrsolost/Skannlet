package com.jrs.skannlet.ui.collections

import android.content.Context
import android.print.PrintManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jrs.skannlet.R
import com.jrs.skannlet.app.CollectionDetailUiState
import com.jrs.skannlet.app.CollectionsUiState
import com.jrs.skannlet.app.ScanRowUiState
import com.jrs.skannlet.data.export.CollectionPrintDocument
import com.jrs.skannlet.data.export.CollectionPrintRow
import com.jrs.skannlet.data.export.collectionDocumentType
import com.jrs.skannlet.data.export.collectionPrintAdapter
import com.jrs.skannlet.data.export.collectionPrintAttributes
import com.jrs.skannlet.data.model.MAX_SCAN_ROW_COMMENT_LENGTH
import com.jrs.skannlet.ui.components.NameInputDialog
import com.jrs.skannlet.ui.components.QuantityControls
import com.jrs.skannlet.util.formatDateTime
import com.jrs.skannlet.util.formatQuantity

@Composable
fun CollectionDetailRoute(
    uiState: CollectionsUiState,
    printingLabelRowId: String?,
    onBack: () -> Unit,
    onSetActiveCollection: (String) -> Unit,
    onRenameCollection: (String, String) -> Unit,
    onUnlockCollection: (String) -> Unit,
    onDeleteCollection: (String) -> Unit,
    onUpdateQuantity: (String, Float) -> Unit,
    onUpdateScanRowComment: (String, String) -> Unit,
    onDeleteScanRow: (String) -> Unit,
    onExportCollection: (String, CollectionPrintDocument, String) -> Unit,
    onPrintLabel: (String) -> Unit,
    onScanCollection: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var commentRowId by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val detail = uiState.detail

    if (detail == null) {
        MissingCollection(onBack = onBack, modifier = modifier)
        return
    }

    CollectionDetailScreen(
        detail = detail,
        printingLabelRowId = printingLabelRowId,
        onBack = onBack,
        onSetActive = { onSetActiveCollection(detail.id) },
        onRename = { showRenameDialog = true },
        onUnlock = { onUnlockCollection(detail.id) },
        onDelete = { showDeleteDialog = true },
        onQuantityChange = onUpdateQuantity,
        onEditComment = { commentRowId = it },
        onDeleteRow = onDeleteScanRow,
        onExport = {
            onExportCollection(
                detail.id,
                detail.toPrintDocument(),
                detail.printFileName(),
            )
        },
        onPrintCollection = { printCollection(context, detail) },
        onPrintLabel = onPrintLabel,
        onScanCollection = { onScanCollection(detail.id) },
        modifier = modifier,
    )

    if (showRenameDialog) {
        NameInputDialog(
            title = "Endre navn",
            label = "Navn på prosjekt",
            confirmText = "Lagre",
            initialValue = detail.name,
            onConfirm = { name ->
                showRenameDialog = false
                onRenameCollection(detail.id, name)
            },
            onDismiss = { showRenameDialog = false },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Slett prosjekt") },
            text = { Text("Prosjekt og alle scannede rader slettes lokalt.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    onClick = {
                        showDeleteDialog = false
                        onDeleteCollection(detail.id)
                        onBack()
                    },
                ) {
                    Text("Slett")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Avbryt")
                }
            },
        )
    }


    commentRowId?.let { rowId ->
        val row = detail.rows.firstOrNull { it.id == rowId }
        if (row == null || detail.isLocked) {
            commentRowId = null
        } else {
            ScanRowCommentDialog(
                initialComment = row.comment,
                onConfirm = { comment ->
                    commentRowId = null
                    onUpdateScanRowComment(row.id, comment)
                },
                onDismiss = { commentRowId = null },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionDetailScreen(
    detail: CollectionDetailUiState,
    printingLabelRowId: String?,
    onBack: () -> Unit,
    onSetActive: () -> Unit,
    onRename: () -> Unit,
    onUnlock: () -> Unit,
    onDelete: () -> Unit,
    onQuantityChange: (String, Float) -> Unit,
    onEditComment: (String) -> Unit,
    onDeleteRow: (String) -> Unit,
    onExport: () -> Unit,
    onPrintCollection: () -> Unit,
    onPrintLabel: (String) -> Unit,
    onScanCollection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize(),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CollectionDetailTopAppBar(
                title = detail.name,
                isReturn = detail.isReturn,
                isActive = detail.isActive,
                isLocked = detail.isLocked,
                onBack = onBack,
                onSetActive = onSetActive,
                onUnlock = onUnlock,
                onDelete = onDelete,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                    Text(
                        text = "${detail.scanCount} skanninger",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Sist endret: ${formatDateTime(detail.updatedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "Opprettet av: ${detail.creatorName?.takeIf { it.isNotBlank() } ?: "Ukjent bruker"}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (detail.isLocked) {
                    AssistChip(onClick = {}, label = { Text("Låst") })
                } else if (detail.isActive) {
                    AssistChip(onClick = {}, label = { Text("Aktiv") })
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onExport) {
                    Icon(
                        painter = painterResource(R.drawable.attach_email_24px),
                        contentDescription = "Eksporter CSV",
                    )
                }
                Button(onClick = onPrintCollection) {
                    Icon(
                        painter = painterResource(R.drawable.print_24px),
                        contentDescription = "Skriv ut",
                    )
                }
                OutlinedButton(
                    enabled = !detail.isLocked,
                    onClick = onRename,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.edit_24px),
                        contentDescription = "Endre navn",
                    )
                }
                OutlinedButton(
                    enabled = !detail.isLocked,
                    onClick = onScanCollection,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.barcode_scanner_24px),
                        contentDescription = "Skann varer",
                    )
                }
            }

            if (detail.rows.isEmpty()) {
                EmptyRows(
                    onScanCollection = onScanCollection,
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    items(detail.rows, key = { it.id }) { row ->
                        ScanRowItem(
                            row = row,
                            isCollectionLocked = detail.isLocked,
                            isPrintingLabel = printingLabelRowId == row.id,
                            isPrintLabelEnabled = printingLabelRowId == null,
                            onQuantityChange = { quantity -> onQuantityChange(row.id, quantity) },
                            onEditComment = { onEditComment(row.id) },
                            onDelete = { onDeleteRow(row.id) },
                            onPrintLabel = { onPrintLabel(row.id) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionDetailTopAppBar(
    title: String,
    isReturn: Boolean = false,
    isActive: Boolean,
    isLocked: Boolean,
    onBack: () -> Unit,
    onSetActive: () -> Unit,
    onUnlock: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }

    TopAppBar(
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isReturn) ReturnTag()
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back_24px),
                    contentDescription = "Tilbake",
                )
            }
        },
        actions = {
            if (isLocked) {
                IconButton(enabled = false, onClick = {}) {
                    Icon(
                        painter = painterResource(R.drawable.lock_24px),
                        contentDescription = "Prosjekt låst",
                    )
                }
            } else if (!isActive) {
                TextButton(onClick = onSetActive) {
                    Text("Sett aktiv")
                }
            }
            if (onDelete != null) {
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
                    if (isLocked) {
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
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        },
    )
}

private fun printCollection(
    context: Context,
    detail: CollectionDetailUiState,
) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val jobName = detail.printJobName()
    val printDocument = detail.toPrintDocument()
    printManager.print(
        jobName,
        collectionPrintAdapter(context, printDocument, jobName),
        collectionPrintAttributes(),
    )
}

private fun String.sanitizePrintJobName(): String {
    val sanitized = trim()
        .replace(Regex("\\s+"), "_")
        .replace(Regex("[^\\p{L}\\p{N}._-]"), "")
        .take(64)

    return sanitized.ifBlank { "Prosjekt" }
}

internal fun CollectionDetailUiState.printJobName(): String =
    "${collectionDocumentType(isReturn)}_$name".sanitizePrintJobName()

internal fun CollectionDetailUiState.printFileName(): String =
    "${printJobName()}.pdf"

private fun CollectionDetailUiState.printTitle(): String {
    val documentType = collectionDocumentType(isReturn)
    return "$documentType #$projectNumber | $name"
}

internal fun CollectionDetailUiState.toPrintDocument(): CollectionPrintDocument {
    val creator = creatorName?.takeIf { it.isNotBlank() } ?: "Ukjent bruker"
    return CollectionPrintDocument(
        title = printTitle(),
        metaText = "$scanCount skanninger | Sist endret: ${formatDateTime(updatedAt)} | Opprettet av: $creator",
        rows = rows.map { row ->
            CollectionPrintRow(
                quantity = formatQuantity(row.quantity),
                barcode = row.barcode,
                productName = row.productName,
                createdAt = formatDateTime(row.createdAt),
                comment = row.comment,
            )
        },
    )
}

@Composable
private fun MissingCollection(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize(),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CollectionDetailTopAppBar(
                title = "Prosjekt",
                isActive = true,
                isLocked = false,
                onBack = onBack,
                onSetActive = {},
                onUnlock = {},
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Prosjektet finnes ikke.", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun EmptyRows(
    onScanCollection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Ingen varer enda",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Skann varer for å fylle prosjektet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Image(
            painter = painterResource(R.drawable.collectionsempty),
            contentDescription = null,
            modifier = Modifier.size(180.dp),
        )
        Button(
            onClick = onScanCollection,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
        ) {
            Text("Skann varer")
        }
    }
}

@Composable
private fun ScanRowItem(
    row: ScanRowUiState,
    isCollectionLocked: Boolean,
    isPrintingLabel: Boolean,
    isPrintLabelEnabled: Boolean,
    onQuantityChange: (Float) -> Unit,
    onEditComment: () -> Unit,
    onDelete: () -> Unit,
    onPrintLabel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    Text(
                        text = row.productName,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Strekkode: ${row.barcode}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (!isCollectionLocked) {
                    IconButton(
                        enabled = !isPrintingLabel,
                        onClick = onDelete,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.delete_24px),
                            contentDescription = "Slett",
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (isCollectionLocked) {
                        Text(
                            "Antall: ${formatQuantity(row.quantity)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else if (row.quantityLocked) {
                        Text("Antall låst: 1", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        QuantityControls(
                            quantity = row.quantity,
                            onQuantityChange = onQuantityChange,
                            enabled = !isPrintingLabel,
                            compact = true,
                        )
                    }
                }
                Row {
                    IconButton(
                        enabled = !isCollectionLocked && !isPrintingLabel,
                        onClick = onEditComment,
                        modifier = Modifier.semantics {
                            contentDescription = if (row.comment.isBlank()) {
                                "Legg til kommentar for ${row.barcode}"
                            } else {
                                "Rediger kommentar for ${row.barcode}"
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.comment_24px),
                            contentDescription = null,
                        )
                    }
                    IconButton(
                        enabled = isPrintLabelEnabled,
                        onClick = onPrintLabel,
                        modifier = Modifier.semantics {
                            contentDescription = if (isPrintingLabel) {
                                "Skriver ut etikett for ${row.barcode}"
                            } else {
                                "Skriv ut etikett for ${row.barcode}"
                            }
                        },
                    ) {
                        if (isPrintingLabel) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.print_24px),
                                contentDescription = null,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanRowCommentDialog(
    initialComment: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var comment by rememberSaveable(initialComment) { mutableStateOf(initialComment) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialComment.isBlank()) "Legg til kommentar" else "Rediger kommentar") },
        text = {
            OutlinedTextField(
                value = comment,
                onValueChange = { value ->
                    comment = value.take(MAX_SCAN_ROW_COMMENT_LENGTH)
                },
                label = { Text("Kommentar") },
                supportingText = {
                    Text("${comment.length}/$MAX_SCAN_ROW_COMMENT_LENGTH")
                },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(comment) }) {
                Text("Lagre")
            }
        },
        dismissButton = {
            Row {
                if (initialComment.isNotBlank()) {
                    TextButton(onClick = { onConfirm("") }) {
                        Text("Fjern")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Avbryt")
                }
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun CollectionDetailScreenPreview() {
    CollectionDetailScreen(
        detail = CollectionDetailUiState(
            id = "1",
            projectNumber = 1,
            name = "1888 Kongsvinger",
            creatorName = "Kari Nordmann",
            isReturn = true,
            scanCount = 2,
            updatedAt = System.currentTimeMillis(),
            isActive = true,
            isLocked = false,
            rows = listOf(
                ScanRowUiState(
                    id = "r1",
                    barcode = "123456",
                    productName = "Demo vare - seks siffer",
                    quantity = 3f,
                    quantityLocked = false,
                    createdAt = System.currentTimeMillis(),
                ),
                ScanRowUiState(
                    id = "r2",
                    barcode = "7038010000017",
                    productName = "Demo vare - strekkode",
                    quantity = 1f,
                    quantityLocked = true,
                    createdAt = System.currentTimeMillis(),
                ),
            ),
        ),
        printingLabelRowId = "r2",
        onBack = {},
        onSetActive = {},
        onRename = {},
        onUnlock = {},
        onDelete = {},
        onQuantityChange = { _, _ -> },
        onEditComment = {},
        onDeleteRow = {},
        onExport = {},
        onPrintCollection = {},
        onPrintLabel = {},
        onScanCollection = {},
    )
}
