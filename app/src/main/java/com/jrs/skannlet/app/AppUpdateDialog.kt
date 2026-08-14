package com.jrs.skannlet.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jrs.skannlet.update.UpdateDownloadPhase
import java.util.Locale

@Composable
internal fun AppUpdateDialog(
    state: AppUpdateUiState,
    onBackupAndUpdate: () -> Unit,
    onUpdate: () -> Unit,
    onInstall: () -> Unit,
    onLater: () -> Unit,
    onDismiss: () -> Unit,
    onHideDownload: () -> Unit,
    onCancelDownload: () -> Unit,
) {
    val release = state.release ?: return
    when (state.status) {
        AppUpdateStatus.Available -> AlertDialog(
            onDismissRequest = onLater,
            title = { Text("Ny versjon ${release.version}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("En ny stabil versjon av Skannlet er tilgjengelig (${formatMegabytes(release.assetSize)} MB).")
                    if (release.notes.isNotBlank()) Text(release.notes.take(1_500))
                    Text("Installer oppdateringen over eksisterende app. Ikke avinstaller først.")
                }
            },
            confirmButton = {
                Button(onClick = onUpdate) { Text("Oppdater") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = onBackupAndUpdate) { Text("Ta sikkerhetskopi") }
                    TextButton(onClick = onLater) { Text("Senere") }
                }
            },
        )

        AppUpdateStatus.Downloading -> AlertDialog(
            onDismissRequest = onHideDownload,
            title = { Text("Laster ned ${release.version}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val progress = state.progressPercent
                    if (progress == null) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("$progress %")
                    }
                    Text(
                        when (state.downloadPhase) {
                            UpdateDownloadPhase.Pending -> "Nedlastingen venter på å starte."
                            UpdateDownloadPhase.Paused -> "Nedlastingen er satt på pause og fortsetter når Android tillater det."
                            UpdateDownloadPhase.Verifying -> "APK-filen kontrolleres før installasjon."
                            UpdateDownloadPhase.Running,
                            null,
                            -> "Nedlastingen pågår."
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onHideDownload) { Text("Skjul") }
            },
            dismissButton = {
                TextButton(onClick = onCancelDownload) { Text("Avbryt") }
            },
        )

        AppUpdateStatus.Ready -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Oppdateringen er klar") },
            text = { Text("Android ber deg bekrefte installasjonen. Appdata beholdes ved oppdatering på stedet.") },
            confirmButton = {
                Button(onClick = onInstall) { Text("Installer") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Lukk") }
            },
        )

        AppUpdateStatus.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Oppdateringen feilet") },
            text = { Text(state.errorMessage ?: "Oppdateringen kunne ikke fullføres.") },
            confirmButton = {
                Button(onClick = onUpdate) { Text("Prøv igjen") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Lukk") }
            },
        )

        AppUpdateStatus.Idle,
        AppUpdateStatus.Checking,
        -> Unit
    }
}

private fun formatMegabytes(bytes: Long): String =
    String.format(Locale.getDefault(), "%.1f", bytes / 1_048_576.0)
