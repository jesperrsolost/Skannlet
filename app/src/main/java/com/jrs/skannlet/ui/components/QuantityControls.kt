package com.jrs.skannlet.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jrs.skannlet.R
import com.jrs.skannlet.util.decrementQuantity
import com.jrs.skannlet.util.formatQuantity
import com.jrs.skannlet.util.incrementQuantity
import com.jrs.skannlet.util.parseQuantity

@Composable
fun QuantityControls(
    quantity: Float,
    onQuantityChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    var showQuantityDialog by rememberSaveable { mutableStateOf(false) }
    val decrementedQuantity = decrementQuantity(quantity)
    val incrementedQuantity = incrementQuantity(quantity)
    val buttonModifier = if (compact) Modifier.size(40.dp) else Modifier
    val buttonContentPadding = if (compact) PaddingValues(0.dp) else ButtonDefaults.ContentPadding

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
    ) {
        Text("Antall", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(
            enabled = enabled && decrementedQuantity != null,
            onClick = { decrementedQuantity?.let(onQuantityChange) },
            modifier = buttonModifier,
            contentPadding = buttonContentPadding,
        ) {
            Icon(
                painter = painterResource(R.drawable.remove_24px),
                contentDescription = "Reduser antall",
            )
        }
        TextButton(
            enabled = enabled,
            onClick = { showQuantityDialog = true },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            modifier = Modifier.widthIn(min = 48.dp),
        ) {
            Text(formatQuantity(quantity), style = MaterialTheme.typography.titleMedium)
        }
        OutlinedButton(
            enabled = enabled && incrementedQuantity != null,
            onClick = { incrementedQuantity?.let(onQuantityChange) },
            modifier = buttonModifier,
            contentPadding = buttonContentPadding,
        ) {
            Icon(
                painter = painterResource(R.drawable.add_24px),
                contentDescription = "Øk antall",
            )
        }
    }

    if (showQuantityDialog) {
        QuantityInputDialog(
            initialQuantity = quantity,
            onConfirm = { newQuantity ->
                showQuantityDialog = false
                onQuantityChange(newQuantity)
            },
            onDismiss = { showQuantityDialog = false },
        )
    }
}

@Composable
private fun QuantityInputDialog(
    initialQuantity: Float,
    onConfirm: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by rememberSaveable(initialQuantity) { mutableStateOf(formatQuantity(initialQuantity)) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val quantity = parseQuantity(value)
    val isValid = quantity != null

    fun submit() {
        quantity?.let(onConfirm)
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Endre antall") },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { input ->
                        value = input.filter { it.isDigit() || it == ',' || it == '.' }
                    },
                    label = { Text("Antall") },
                    singleLine = true,
                    isError = value.isNotBlank() && !isValid,
                    supportingText = {
                        if (value.isNotBlank() && !isValid) {
                            Text("Skriv inn et tall større enn 0 med maks tre desimaler")
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = isValid,
                onClick = { submit() },
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
