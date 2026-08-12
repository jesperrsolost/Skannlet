package com.jrs.skannlet.ui.profile.dialogs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

@Composable
internal fun ProjectNumberDialog(
    initialProjectNumber: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by rememberSaveable(initialProjectNumber) {
        mutableStateOf(initialProjectNumber.coerceAtLeast(1).toString())
    }
    val projectNumber = value.toIntOrNull()
    val isValid = projectNumber != null && projectNumber > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sett løpenummer") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { nextValue ->
                    value = nextValue.filter(Char::isDigit).take(9)
                },
                label = { Text("Neste løpenummer") },
                singleLine = true,
                isError = value.isNotBlank() && !isValid,
                supportingText = {
                    if (value.isNotBlank() && !isValid) {
                        Text("Bruk et tall over 0")
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                enabled = isValid,
                onClick = { onConfirm(projectNumber ?: return@Button) },
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
internal fun DeleteUserDialog(
    userName: String?,
    canDelete: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    DestructiveConfirmationDialog(
        title = "Slett bruker",
        message = "Slett ${userName ?: "aktiv bruker"} lokalt? Prosjekter og skanninger beholdes.",
        confirmEnabled = canDelete,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
internal fun DeleteProductsDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    DestructiveConfirmationDialog(
        title = "Slett produktliste",
        message = "Dette sletter den lokale produktlisten og setter eksisterende produktnavn til Ukjent vare.",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
private fun DestructiveConfirmationDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmEnabled: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                enabled = confirmEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                onClick = onConfirm,
            ) {
                Text("Slett")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Avbryt")
            }
        },
    )
}
