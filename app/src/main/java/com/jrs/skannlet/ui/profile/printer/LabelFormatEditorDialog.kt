package com.jrs.skannlet.ui.profile.printer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jrs.skannlet.printer.LabelFormat
import com.jrs.skannlet.printer.LabelMediaTracking
import com.jrs.skannlet.printer.validateLabelFormat

@Composable
internal fun LabelFormatEditorDialog(
    initialFormat: LabelFormat,
    existingFormats: List<LabelFormat>,
    onSave: (LabelFormat) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable(initialFormat.id) { mutableStateOf(initialFormat.name) }
    var width by rememberSaveable(initialFormat.id) {
        mutableStateOf(initialFormat.widthMm.toEditableNumber())
    }
    var height by rememberSaveable(initialFormat.id) {
        mutableStateOf(initialFormat.heightMm.toEditableNumber())
    }
    var trackingHeight by rememberSaveable(initialFormat.id) {
        mutableStateOf(initialFormat.trackingHeightMm.toEditableNumber())
    }
    var mediaTrackingName by rememberSaveable(initialFormat.id) {
        mutableStateOf(initialFormat.mediaTracking.name)
    }
    var barcodeHeight by rememberSaveable(initialFormat.id) {
        mutableStateOf(initialFormat.barcodeHeightMm.toEditableNumber())
    }
    var moduleWidth by rememberSaveable(initialFormat.id) {
        mutableStateOf(initialFormat.moduleWidthDots.toString())
    }

    val mediaTracking = LabelMediaTracking.valueOf(mediaTrackingName)
    val candidate = buildEditedLabelFormat(
        initialFormat = initialFormat,
        name = name,
        width = width,
        height = height,
        trackingHeight = trackingHeight,
        barcodeHeight = barcodeHeight,
        moduleWidth = moduleWidth,
        mediaTracking = mediaTracking,
    )
    val validationMessage = validateLabelFormat(candidate) ?: if (
        existingFormats.any { format ->
            format.id != candidate.id &&
                format.name.trim().equals(candidate.name.trim(), ignoreCase = true)
        }
    ) {
        "Et format med dette navnet finnes allerede."
    } else {
        null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Etikettformat") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Målene må samsvare med etikettene som er lagt i skriveren.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FormatTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Navn",
                    keyboardType = KeyboardType.Text,
                )
                FormatTextField(
                    value = width,
                    onValueChange = { width = it.toDecimalInput() },
                    label = "Bredde (mm)",
                )
                FormatTextField(
                    value = height,
                    onValueChange = { height = it.toDecimalInput() },
                    label = "Høyde (mm)",
                )
                Text(
                    text = "Medietype",
                    style = MaterialTheme.typography.labelLarge,
                )
                Column(modifier = Modifier.selectableGroup()) {
                    LabelMediaTracking.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = mediaTracking == option,
                                    role = Role.RadioButton,
                                    onClick = { mediaTrackingName = option.name },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = mediaTracking == option,
                                onClick = null,
                            )
                            Text(option.displayName())
                        }
                    }
                }
                if (mediaTracking != LabelMediaTracking.Continuous) {
                    FormatTextField(
                        value = trackingHeight,
                        onValueChange = { trackingHeight = it.toDecimalInput() },
                        label = when (mediaTracking) {
                            LabelMediaTracking.Gap -> "Mellomrom (mm)"
                            LabelMediaTracking.BlackMark -> "Svartmerkehøyde (mm)"
                            LabelMediaTracking.Continuous -> error("Handled above")
                        },
                    )
                }
                FormatTextField(
                    value = barcodeHeight,
                    onValueChange = { barcodeHeight = it.toDecimalInput() },
                    label = "Strekkodehøyde (mm)",
                )
                FormatTextField(
                    value = moduleWidth,
                    onValueChange = { moduleWidth = it.filter(Char::isDigit).take(1) },
                    label = "Modulbredde (punkter)",
                    keyboardType = KeyboardType.Number,
                )
                validationMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = validationMessage == null,
                onClick = { onSave(candidate) },
            ) {
                Text("Lagre")
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
private fun FormatTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Decimal,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun LabelMediaTracking.displayName(): String = when (this) {
    LabelMediaTracking.Gap -> "Etiketter med mellomrom"
    LabelMediaTracking.BlackMark -> "Etiketter med svartmerke"
    LabelMediaTracking.Continuous -> "Kontinuerlig materiale"
}
