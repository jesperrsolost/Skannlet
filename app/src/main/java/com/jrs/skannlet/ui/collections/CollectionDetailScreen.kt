package com.jrs.skannlet.ui.collections

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jrs.skannlet.R
import com.jrs.skannlet.app.CollectionDetailUiState
import com.jrs.skannlet.app.CollectionPrintDocument
import com.jrs.skannlet.app.CollectionPrintRow
import com.jrs.skannlet.app.CollectionsUiState
import com.jrs.skannlet.app.ScanRowUiState
import com.jrs.skannlet.ui.components.NameInputDialog
import com.jrs.skannlet.ui.components.QuantityControls
import com.jrs.skannlet.util.formatDateTime

@Composable
fun CollectionDetailRoute(
    uiState: CollectionsUiState,
    activeUserName: String?,
    onBack: () -> Unit,
    onSetActiveCollection: (String) -> Unit,
    onRenameCollection: (String, String) -> Unit,
    onUnlockCollection: (String) -> Unit,
    onDeleteCollection: (String) -> Unit,
    onUpdateQuantity: (String, Int) -> Unit,
    onDeleteScanRow: (String) -> Unit,
    onExportCollection: (String, CollectionPrintDocument, String) -> Unit,
    onScanCollection: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val detail = uiState.detail

    if (detail == null) {
        MissingCollection(onBack = onBack, modifier = modifier)
        return
    }

    CollectionDetailScreen(
        detail = detail,
        onBack = onBack,
        onSetActive = { onSetActiveCollection(detail.id) },
        onRename = { showRenameDialog = true },
        onUnlock = { onUnlockCollection(detail.id) },
        onDelete = { showDeleteDialog = true },
        onQuantityChange = onUpdateQuantity,
        onDeleteRow = onDeleteScanRow,
        onExport = {
            onExportCollection(
                detail.id,
                detail.toPrintDocument(activeUserName),
                detail.printFileName(),
            )
        },
        onPrint = { printCollection(context, detail, activeUserName) },
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionDetailScreen(
    detail: CollectionDetailUiState,
    onBack: () -> Unit,
    onSetActive: () -> Unit,
    onRename: () -> Unit,
    onUnlock: () -> Unit,
    onDelete: () -> Unit,
    onQuantityChange: (String, Int) -> Unit,
    onDeleteRow: (String) -> Unit,
    onExport: () -> Unit,
    onPrint: () -> Unit,
    onScanCollection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize(),
        topBar = {
            CollectionDetailTopAppBar(
                title = detail.name,
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
                Button(onClick = onPrint) {
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
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(detail.rows, key = { it.id }) { row ->
                        ScanRowItem(
                            row = row,
                            isCollectionLocked = detail.isLocked,
                            onQuantityChange = { quantity -> onQuantityChange(row.id, quantity) },
                            onDelete = { onDeleteRow(row.id) },
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
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
    activeUserName: String?,
) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val webView = WebView(context)
    val jobName = "Samling_${detail.name}".sanitizePrintJobName()

    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) {
            val adapter = view.createPrintDocumentAdapter(jobName)
            printManager.print(
                jobName,
                adapter,
                PrintAttributes.Builder().build(),
            )
        }
    }
    webView.loadDataWithBaseURL(
        null,
        context.collectionPrintHtml(detail, activeUserName),
        "text/html",
        "UTF-8",
        null,
    )
}

private fun String.sanitizePrintJobName(): String {
    val sanitized = trim()
        .replace(Regex("\\s+"), "_")
        .replace(Regex("[^\\p{L}\\p{N}._-]"), "")
        .take(64)

    return sanitized.ifBlank { "Prosjekt" }
}

private fun CollectionDetailUiState.printFileName(): String =
    "${"Samling_$name".sanitizePrintJobName()}.pdf"

private fun CollectionDetailUiState.printTitle(): String =
    "Følgeseddel #$projectNumber | $name"

private fun Context.collectionPrintHtml(
    detail: CollectionDetailUiState,
    activeUserName: String?,
): String = detail.toPrintHtml(
    activeUserName = activeUserName,
    logoDataUri = pngResourceDataUri(R.drawable.omflogo),
)

private fun Context.pngResourceDataUri(resourceId: Int): String =
    resources.openRawResource(resourceId).use { inputStream ->
        "data:image/png;base64,${Base64.encodeToString(inputStream.readBytes(), Base64.NO_WRAP)}"
    }

private fun CollectionDetailUiState.toPrintDocument(activeUserName: String?): CollectionPrintDocument {
    val printedBy = activeUserName?.takeIf { it.isNotBlank() } ?: "Ukjent bruker"
    return CollectionPrintDocument(
        title = printTitle(),
        metaText = "$scanCount skanninger | Sist endret: ${formatDateTime(updatedAt)} | printet av: $printedBy",
        rows = rows.map { row ->
            CollectionPrintRow(
                quantity = row.quantity.toString(),
                barcode = row.barcode,
                productName = row.productName,
                createdAt = formatDateTime(row.createdAt),
            )
        },
    )
}

private fun CollectionDetailUiState.toPrintHtml(
    activeUserName: String?,
    logoDataUri: String,
): String {
    val printedBy = activeUserName?.takeIf { it.isNotBlank() } ?: "Ukjent bruker"
    val rowsHtml = rows.joinToString(separator = "\n") { row ->
        """
        <tr>
            <td>${row.quantity}</td>
            <td></td>
            <td></td>
            <td>${row.barcode.escapeHtml()}</td>
            <td>${row.productName.escapeHtml()}</td>
            <td>${formatDateTime(row.createdAt).escapeHtml()}</td>
            <td></td>
        </tr>
        """.trimIndent()
    }

    return """
        <!doctype html>
        <html lang="no">
        <head>
            <meta charset="utf-8">
            <title>${printTitle().escapeHtml()}</title>
            <style>
                body {
                    color: #1b1b1f;
                    font-family: sans-serif;
                    margin: 32px;
                }
                .print-header {
                    align-items: flex-start;
                    display: flex;
                    gap: 24px;
                    justify-content: space-between;
                    margin-bottom: 8px;
                }
                h1 {
                    font-size: 24px;
                    margin: 0;
                }
                .logo {
                    height: 48px;
                    max-width: 180px;
                    object-fit: contain;
                }
                .meta {
                    color: #555862;
                    font-size: 13px;
                    margin-bottom: 24px;
                }
                table {
                    border-collapse: collapse;
                    width: 100%;
                }
                th,
                td {
                    border-bottom: 1px solid #d7d8df;
                    padding: 8px;
                    text-align: left;
                    vertical-align: top;
                }
                th {
                    background: #f0f1f7;
                    font-weight: 700;
                }
                td:nth-child(1),
                th:nth-child(1),
                td:nth-child(2),
                th:nth-child(2),
                td:nth-child(3),
                th:nth-child(3) {
                    text-align: right;
                    width: 64px;
                }
            </style>
        </head>
        <body>
            <div class="print-header">
                <h1>${printTitle().escapeHtml()}</h1>
                <img class="logo" src="${logoDataUri.escapeHtml()}" alt="OM Fjeld logo">
            </div>
            <div class="meta">
                ${scanCount} skanninger | Sist endret: ${formatDateTime(updatedAt).escapeHtml()} | printet av: ${printedBy.escapeHtml()}
            </div>
            <table>
                <thead>
                    <tr>
                        <th>Ant. bestilt</th>
                        <th>Ant. levert</th>
                        <th>Rest</th>
                        <th>Strekkode</th>
                        <th>Produkt</th>
                        <th>Opprettet</th>
                        <th>Kommentar</th>
                    </tr>
                </thead>
                <tbody>
                    $rowsHtml
                </tbody>
            </table>
        </body>
        </html>
    """.trimIndent()
}

private fun String.escapeHtml(): String = buildString(length) {
    this@escapeHtml.forEach { char ->
        when (char) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(char)
        }
    }
}

@Composable
private fun MissingCollection(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize(),
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
    onQuantityChange: (Int) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = row.productName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (!isCollectionLocked) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            painter = painterResource(R.drawable.delete_24px),
                            contentDescription = "Slett",
                        )
                    }
                }
            }
            Text("Strekkode: ${row.barcode}", style = MaterialTheme.typography.bodyMedium)
            if (isCollectionLocked) {
                Text("Antall: ${row.quantity}", style = MaterialTheme.typography.bodyMedium)
            } else if (row.quantityLocked) {
                Text("Antall låst: 1", style = MaterialTheme.typography.bodyMedium)
            } else {
                QuantityControls(
                    quantity = row.quantity,
                    onQuantityChange = onQuantityChange,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CollectionDetailScreenPreview() {
    CollectionDetailScreen(
        detail = CollectionDetailUiState(
            id = "1",
            projectNumber = 1,
            name = "Varetelling juni",
            scanCount = 2,
            updatedAt = System.currentTimeMillis(),
            isActive = true,
            isLocked = false,
            rows = listOf(
                ScanRowUiState(
                    id = "r1",
                    barcode = "123456",
                    productName = "Demo vare - seks siffer",
                    quantity = 3,
                    quantityLocked = false,
                    createdAt = System.currentTimeMillis(),
                ),
                ScanRowUiState(
                    id = "r2",
                    barcode = "7038010000017",
                    productName = "Demo vare - strekkode",
                    quantity = 1,
                    quantityLocked = true,
                    createdAt = System.currentTimeMillis(),
                ),
            ),
        ),
        onBack = {},
        onSetActive = {},
        onRename = {},
        onUnlock = {},
        onDelete = {},
        onQuantityChange = { _, _ -> },
        onDeleteRow = {},
        onExport = {},
        onPrint = {},
        onScanCollection = {},
    )
}
