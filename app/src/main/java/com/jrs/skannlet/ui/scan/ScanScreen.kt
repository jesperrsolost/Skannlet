package com.jrs.skannlet.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jrs.skannlet.R
import com.jrs.skannlet.app.ScanRowUiState
import com.jrs.skannlet.app.ScanUiState
import com.jrs.skannlet.app.UserUiState
import com.jrs.skannlet.ui.components.AppHeader
import com.jrs.skannlet.ui.components.QuantityControls
import com.jrs.skannlet.ui.components.UserPickerDialog
import com.jrs.skannlet.ui.scan.components.ScannerInputHandler
import com.jrs.skannlet.util.formatDateTime

@Composable
fun ScanRoute(
    uiState: ScanUiState,
    activeUserName: String?,
    users: List<UserUiState>,
    onScan: (String) -> Unit,
    onUpdateQuantity: (String, Int) -> Unit,
    onDeleteRow: (String) -> Unit,
    onSelectCollection: () -> Unit,
    onSetActiveUser: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showUserPicker by rememberSaveable { mutableStateOf(false) }

    ScanScreen(
        uiState = uiState,
        activeUserName = activeUserName,
        onScan = onScan,
        onUpdateQuantity = onUpdateQuantity,
        onDeleteRow = onDeleteRow,
        onSelectCollection = onSelectCollection,
        onChangeUserClick = { showUserPicker = true },
        modifier = modifier,
    )

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
}

@Composable
fun ScanScreen(
    uiState: ScanUiState,
    activeUserName: String?,
    onScan: (String) -> Unit,
    onUpdateQuantity: (String, Int) -> Unit,
    onDeleteRow: (String) -> Unit,
    onSelectCollection: () -> Unit,
    onChangeUserClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppHeader(
            title = "Skanning",
            activeUserName = activeUserName,
            onChangeUserClick = onChangeUserClick,
            modifier = Modifier.fillMaxWidth(),
        )

        if (!uiState.hasActiveCollection) {
            MissingActiveCollection(onSelectCollection = onSelectCollection)
        } else {
            ActiveScanCard(uiState = uiState)
            ScannerInputHandler(
                enabled = true,
                onScan = onScan,
            )
            uiState.latestScannedRow?.let { row ->
                LatestScannedItemCard(
                    row = row,
                    onQuantityChange = { quantity -> onUpdateQuantity(row.id, quantity) },
                    onDelete = { onDeleteRow(row.id) },
                )
            }
        }
    }
}

@Composable
private fun ActiveScanCard(
    uiState: ScanUiState,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Aktivt prosjekt",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = uiState.activeCollectionName.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = uiState.status,
                style = MaterialTheme.typography.bodyLarge,
            )
            uiState.lastMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun LatestScannedItemCard(
    row: ScanRowUiState,
    onQuantityChange: (Int) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Sist skannet",
                style = MaterialTheme.typography.labelLarge,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = row.productName,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        painter = painterResource(R.drawable.delete_24px),
                        contentDescription = "Slett",
                    )
                }
            }
            Text(
                text = "Strekkode: ${row.barcode}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (row.quantityLocked) {
                Text(
                    text = "Antall låst: 1",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                QuantityControls(
                    quantity = row.quantity,
                    onQuantityChange = onQuantityChange,
                )
            }
            Text(
                text = "Tidspunkt: ${formatDateTime(row.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun MissingActiveCollection(
    onSelectCollection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Ingen aktivt prosjekt valgt",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Velg eller opprett et prosjekt før du scanner.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onSelectCollection) {
                Text("Velg/opprett prosjekt")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ScanScreenPreview() {
    ScanScreen(
        uiState = ScanUiState(
            activeCollectionName = "Varetelling juni",
            hasActiveCollection = true,
            lastMessage = "Registrert: 123456",
            latestScannedRow = ScanRowUiState(
                id = "1",
                barcode = "123456",
                productName = "Demo vare - seks siffer",
                quantity = 1,
                quantityLocked = false,
                createdAt = System.currentTimeMillis(),
            ),
        ),
        activeUserName = "Ola Nordmann",
        onScan = {},
        onUpdateQuantity = { _, _ -> },
        onDeleteRow = {},
        onSelectCollection = {},
        onChangeUserClick = {},
    )
}
