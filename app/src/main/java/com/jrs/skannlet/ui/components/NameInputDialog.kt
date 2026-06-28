package com.jrs.skannlet.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun NameInputDialog(
    title: String,
    label: String,
    confirmText: String,
    onConfirm: (String) -> Unit,
    onDismiss: (() -> Unit)? = null,
    initialValue: String = "",
) {
    var value by rememberSaveable(initialValue) { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = { onDismiss?.invoke() },
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(label) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = value.trim().isNotEmpty(),
                onClick = { onConfirm(value.trim()) },
            ) {
                Text(confirmText)
            }
        },
        dismissButton = onDismiss?.let {
            {
                TextButton(onClick = it) {
                    Text("Avbryt")
                }
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun NameInputDialogPreview() {
    NameInputDialog(
        title = "Nytt prosjekt",
        label = "Navn",
        confirmText = "Lagre",
        onConfirm = {},
        onDismiss = {},
    )
}
