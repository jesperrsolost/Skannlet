package com.jrs.skannlet.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.jrs.skannlet.app.UserUiState

@Composable
fun UserPickerDialog(
    users: List<UserUiState>,
    onSelectUser: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bytt bruker") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (users.isEmpty()) {
                    Text("Ingen brukere er opprettet.")
                } else {
                    users.forEach { user ->
                        TextButton(
                            enabled = !user.isActive,
                            onClick = { onSelectUser(user.id) },
                        ) {
                            Text(if (user.isActive) "${user.name} (aktiv)" else user.name)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Avbryt")
            }
        },
    )
}
