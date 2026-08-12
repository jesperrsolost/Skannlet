package com.jrs.skannlet.ui.profile.printer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jrs.skannlet.R
import com.jrs.skannlet.app.LabelPrinterUiState
import com.jrs.skannlet.printer.LabelFormat
import com.jrs.skannlet.printer.LabelPrinterSettings
import com.jrs.skannlet.printer.SmallBarcodeLabelFormat
import com.jrs.skannlet.ui.profile.components.ProfileSubpageHeader
import java.util.UUID

@Composable
internal fun LabelPrinterSettingsScreen(
    state: LabelPrinterUiState,
    onBackClick: () -> Unit,
    onSaveEndpoint: (String, Int) -> Unit,
    onTestConnection: (String, Int) -> Unit,
    onSelectFormat: (String) -> Unit,
    onSaveFormat: (LabelFormat) -> Unit,
    onDeleteFormat: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var ipAddress by rememberSaveable(state.settings.ipAddress) {
        mutableStateOf(state.settings.ipAddress)
    }
    var portText by rememberSaveable(state.settings.port) {
        mutableStateOf(state.settings.port.toString())
    }
    var editedFormatId by rememberSaveable { mutableStateOf<String?>(null) }
    var editedSourceFormatId by rememberSaveable { mutableStateOf<String?>(null) }
    var editedInitialName by rememberSaveable { mutableStateOf<String?>(null) }
    var formatToDeleteId by rememberSaveable { mutableStateOf<String?>(null) }

    fun openFormatEditor(source: LabelFormat, id: String, name: String) {
        editedSourceFormatId = source.id
        editedFormatId = id
        editedInitialName = name
    }

    fun closeFormatEditor() {
        editedFormatId = null
        editedSourceFormatId = null
        editedInitialName = null
    }

    val editedFormat = editedFormatId?.let { id ->
        val source = state.settings.formats.firstOrNull { it.id == editedSourceFormatId }
            ?: SmallBarcodeLabelFormat
        source.copy(id = id, name = editedInitialName ?: source.name)
    }
    val formatToDelete = state.settings.formats.firstOrNull { it.id == formatToDeleteId }

    val trimmedAddress = ipAddress.trim()
    val parsedPort = portText.toIntOrNull()
    val isAddressValid = trimmedAddress.isValidIpv4Address()
    val isPortValid = parsedPort != null && parsedPort in 1..65535
    val canUseEndpoint = isAddressValid && isPortValid && !state.isTesting
    val endpointIsSaved = trimmedAddress == state.settings.ipAddress &&
        parsedPort == state.settings.port

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ProfileSubpageHeader(
                title = "Etikettskriver",
                onBackClick = onBackClick,
            )
        }

        item {
            Text(
                text = "TSC TC200 A1.81 EZ · TSPL over direkte TCP. Standardporten er 9100.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            Text(
                text = "Tilkobling",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        item {
            OutlinedTextField(
                value = ipAddress,
                onValueChange = { value ->
                    ipAddress = value.filter { it.isDigit() || it == '.' }.take(15)
                },
                enabled = !state.isTesting,
                label = { Text("IP-adresse") },
                placeholder = { Text("192.168.1.100") },
                singleLine = true,
                isError = ipAddress.isNotBlank() && !isAddressValid,
                supportingText = {
                    if (ipAddress.isNotBlank() && !isAddressValid) {
                        Text("Skriv inn en gyldig IP-adresse")
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            OutlinedTextField(
                value = portText,
                onValueChange = { portText = it.filter(Char::isDigit).take(5) },
                enabled = !state.isTesting,
                label = { Text("Port") },
                singleLine = true,
                isError = portText.isNotBlank() && !isPortValid,
                supportingText = {
                    if (portText.isNotBlank() && !isPortValid) {
                        Text("Porten må være mellom 1 og 65535")
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    enabled = canUseEndpoint && !endpointIsSaved,
                    onClick = { onSaveEndpoint(trimmedAddress, parsedPort ?: return@Button) },
                ) {
                    Text("Lagre")
                }
                OutlinedButton(
                    enabled = canUseEndpoint && endpointIsSaved,
                    onClick = {
                        onTestConnection(trimmedAddress, parsedPort ?: return@OutlinedButton)
                    },
                ) {
                    if (state.isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Tester")
                    } else {
                        Text("Test tilkobling")
                    }
                }
            }
        }

        if (isAddressValid && isPortValid && !endpointIsSaved) {
            item {
                Text(
                    text = "Lagre tilkoblingen før du tester den.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { HorizontalDivider() }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Etikettformater",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Velg formatet som brukes ved utskrift.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = {
                        openFormatEditor(
                            source = SmallBarcodeLabelFormat,
                            id = UUID.randomUUID().toString(),
                            name = nextFormatName("Eget format", state.settings.formats),
                        )
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.add_24px),
                        contentDescription = null,
                    )
                    Text("Nytt")
                }
            }
        }

        item {
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.settings.formats.forEach { format ->
                    key(format.id) {
                        LabelFormatCard(
                            format = format,
                            selected = format.id == state.settings.selectedFormat.id,
                            onSelect = { onSelectFormat(format.id) },
                            onCopy = {
                                openFormatEditor(
                                    source = format,
                                    id = UUID.randomUUID().toString(),
                                    name = nextFormatName(
                                        "${format.name} kopi",
                                        state.settings.formats,
                                    ),
                                )
                            },
                            onEdit = { openFormatEditor(format, format.id, format.name) },
                            onDelete = { formatToDeleteId = format.id },
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Small Barcode antar etiketter på 35 × 14 mm med 2,25 mm mellomrom. Tilpass en kopi hvis etikettene i skriveren har andre mål.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    editedFormat?.let { format ->
        LabelFormatEditorDialog(
            initialFormat = format,
            existingFormats = state.settings.formats,
            onSave = { saved ->
                closeFormatEditor()
                onSaveFormat(saved)
            },
            onDismiss = ::closeFormatEditor,
        )
    }

    formatToDelete?.let { format ->
        AlertDialog(
            onDismissRequest = { formatToDeleteId = null },
            title = { Text("Slett etikettformat") },
            text = { Text("Vil du slette ${format.name}?") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    onClick = {
                        formatToDeleteId = null
                        onDeleteFormat(format.id)
                    },
                ) {
                    Text("Slett")
                }
            },
            dismissButton = {
                TextButton(onClick = { formatToDeleteId = null }) {
                    Text("Avbryt")
                }
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LabelPrinterSettingsScreenPreview() {
    LabelPrinterSettingsScreen(
        state = LabelPrinterUiState(
            settings = LabelPrinterSettings(
                ipAddress = "192.168.1.42",
            ),
        ),
        onBackClick = {},
        onSaveEndpoint = { _, _ -> },
        onTestConnection = { _, _ -> },
        onSelectFormat = {},
        onSaveFormat = {},
        onDeleteFormat = {},
    )
}
