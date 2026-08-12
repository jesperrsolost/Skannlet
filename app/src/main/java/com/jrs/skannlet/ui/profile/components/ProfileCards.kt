package com.jrs.skannlet.ui.profile.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jrs.skannlet.app.LabelPrinterUiState
import com.jrs.skannlet.app.UserUiState

@Composable
internal fun ActiveUserCard(
    activeUserName: String?,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Aktiv bruker",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = activeUserName ?: "Ingen bruker valgt",
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

@Composable
internal fun ProductImportCard(
    productCount: Int,
    onImportProductsClick: () -> Unit,
    onDeleteProductsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Produktliste",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (productCount > 0) {
                    "$productCount produkter er lagret lokalt. Importer en ny CSV for å oppdatere listen."
                } else {
                    "Ingen produktliste er lagret. Importer CSV med kolonnene Produktnr. og Produkt."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onImportProductsClick) {
                    Text(if (productCount > 0) "Oppdater" else "Importer")
                }
                OutlinedButton(
                    enabled = productCount > 0,
                    onClick = onDeleteProductsClick,
                ) {
                    Text("Slett")
                }
            }
        }
    }
}

@Composable
internal fun LabelPrinterCard(
    state: LabelPrinterUiState,
    onConfigureClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Etikettskriver",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (state.settings.ipAddress.isBlank()) {
                        "Ikke konfigurert · ${state.settings.selectedFormat.name}"
                    } else {
                        "${state.settings.ipAddress}:${state.settings.port} · ${state.settings.selectedFormat.name}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onConfigureClick) {
                Text("Konfigurer")
            }
        }
    }
}

@Composable
internal fun ProfileUserItem(
    user: UserUiState,
    onSetActive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = user.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (user.isActive) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = "Aktiv",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            } else {
                OutlinedButton(onClick = onSetActive) {
                    Text("Bytt")
                }
            }
        }
    }
}
