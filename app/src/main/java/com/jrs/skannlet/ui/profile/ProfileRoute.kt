package com.jrs.skannlet.ui.profile

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.jrs.skannlet.app.LabelPrinterUiState
import com.jrs.skannlet.app.AppUpdateUiState
import com.jrs.skannlet.app.ProfileUiState
import com.jrs.skannlet.printer.LabelFormat
import com.jrs.skannlet.ui.components.NameInputDialog
import com.jrs.skannlet.ui.profile.about.AboutApplicationScreen
import com.jrs.skannlet.ui.profile.about.SOURCE_CODE_URL
import com.jrs.skannlet.ui.profile.dialogs.DeleteProductsDialog
import com.jrs.skannlet.ui.profile.dialogs.DeleteUserDialog
import com.jrs.skannlet.ui.profile.dialogs.ProjectNumberDialog
import com.jrs.skannlet.ui.profile.printer.LabelPrinterSettingsScreen

private const val TEAMVIEWER_QUICKSUPPORT_PACKAGE = "com.teamviewer.quicksupport.market"

private enum class ProfilePage {
    Main,
    About,
    Printer,
}

private enum class ProfileDialog {
    AddUser,
    DeleteUser,
    DeleteProducts,
    ProjectNumber,
}

@Composable
fun ProfileRoute(
    uiState: ProfileUiState,
    labelPrinterState: LabelPrinterUiState,
    appUpdateState: AppUpdateUiState,
    onAddUser: (String) -> Unit,
    onDeleteUser: (String) -> Unit,
    onSetActiveUser: (String) -> Unit,
    onSetNextProjectNumber: (Int) -> Unit,
    onImportProducts: (Uri) -> Unit,
    onDeleteProducts: () -> Unit,
    onSavePrinterEndpoint: (String, Int) -> Unit,
    onTestPrinterConnection: (String, Int) -> Unit,
    onSelectLabelFormat: (String) -> Unit,
    onSaveLabelFormat: (LabelFormat) -> Unit,
    onDeleteLabelFormat: (String) -> Unit,
    onCheckForUpdates: () -> Unit,
    onRequestBackup: () -> Unit,
    onRestoreBackup: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentPage by rememberSaveable { mutableStateOf(ProfilePage.Main) }
    var activeDialog by rememberSaveable { mutableStateOf<ProfileDialog?>(null) }
    var pendingRestoreUri by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val productImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(onImportProducts)
    }
    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> pendingRestoreUri = uri?.toString() }

    when (currentPage) {
        ProfilePage.Main -> ProfileScreen(
            uiState = uiState,
            labelPrinterState = labelPrinterState,
            onAddUserClick = { activeDialog = ProfileDialog.AddUser },
            onDeleteUserClick = { activeDialog = ProfileDialog.DeleteUser },
            onSupportClick = { openTeamViewerQuickSupport(context) },
            onAboutClick = { currentPage = ProfilePage.About },
            onConfigurePrinterClick = { currentPage = ProfilePage.Printer },
            onSetProjectNumberClick = { activeDialog = ProfileDialog.ProjectNumber },
            onSetActiveUser = onSetActiveUser,
            onImportProductsClick = {
                productImportLauncher.launch(
                    arrayOf(
                        "text/*",
                        "text/csv",
                        "text/comma-separated-values",
                        "application/csv",
                        "application/vnd.ms-excel",
                        "application/octet-stream",
                    ),
                )
            },
            onDeleteProductsClick = { activeDialog = ProfileDialog.DeleteProducts },
            modifier = modifier,
        )

        ProfilePage.About -> {
            BackHandler { currentPage = ProfilePage.Main }
            AboutApplicationScreen(
                updateState = appUpdateState,
                onBackClick = { currentPage = ProfilePage.Main },
                onSourceCodeClick = { openSourceCode(context) },
                onCheckForUpdates = onCheckForUpdates,
                onExportBackup = onRequestBackup,
                onRestoreBackup = { restoreBackupLauncher.launch(arrayOf("application/zip")) },
                modifier = modifier,
            )
        }

        ProfilePage.Printer -> {
            BackHandler { currentPage = ProfilePage.Main }
            LabelPrinterSettingsScreen(
                state = labelPrinterState,
                onBackClick = { currentPage = ProfilePage.Main },
                onSaveEndpoint = onSavePrinterEndpoint,
                onTestConnection = onTestPrinterConnection,
                onSelectFormat = onSelectLabelFormat,
                onSaveFormat = onSaveLabelFormat,
                onDeleteFormat = onDeleteLabelFormat,
                modifier = modifier,
            )
        }
    }

    when (activeDialog) {
        ProfileDialog.AddUser -> NameInputDialog(
            title = "Ny bruker",
            label = "Navn",
            confirmText = "Legg til",
            onConfirm = { name ->
                activeDialog = null
                onAddUser(name)
            },
            onDismiss = { activeDialog = null },
        )

        ProfileDialog.DeleteUser -> DeleteUserDialog(
            userName = uiState.activeUserName,
            canDelete = uiState.activeUserId != null,
            onConfirm = {
                val userId = uiState.activeUserId ?: return@DeleteUserDialog
                activeDialog = null
                onDeleteUser(userId)
            },
            onDismiss = { activeDialog = null },
        )

        ProfileDialog.DeleteProducts -> DeleteProductsDialog(
            onConfirm = {
                activeDialog = null
                onDeleteProducts()
            },
            onDismiss = { activeDialog = null },
        )

        ProfileDialog.ProjectNumber -> ProjectNumberDialog(
            initialProjectNumber = uiState.nextCollectionProjectNumber,
            onConfirm = { projectNumber ->
                activeDialog = null
                onSetNextProjectNumber(projectNumber)
            },
            onDismiss = { activeDialog = null },
        )

        null -> Unit
    }

    pendingRestoreUri?.let { uriString ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("Gjenopprett sikkerhetskopi") },
            text = { Text("Alle lokale brukere, prosjekter, varer og appstatus erstattes med innholdet i sikkerhetskopien.") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingRestoreUri = null
                        onRestoreBackup(Uri.parse(uriString))
                    },
                ) { Text("Gjenopprett") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) { Text("Avbryt") }
            },
        )
    }
}

private fun openTeamViewerQuickSupport(context: Context) {
    val launchIntent = context.packageManager
        .getLaunchIntentForPackage(TEAMVIEWER_QUICKSUPPORT_PACKAGE)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    if (launchIntent != null && context.startActivitySafely(launchIntent)) {
        return
    }

    val playStoreIntent = Intent(
        Intent.ACTION_VIEW,
        "market://details?id=$TEAMVIEWER_QUICKSUPPORT_PACKAGE".toUri(),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val browserIntent = Intent(
        Intent.ACTION_VIEW,
        "https://play.google.com/store/apps/details?id=$TEAMVIEWER_QUICKSUPPORT_PACKAGE".toUri(),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    if (!context.startActivitySafely(playStoreIntent)) {
        context.startActivitySafely(browserIntent)
    }
}

private fun openSourceCode(context: Context) {
    val sourceIntent = Intent(
        Intent.ACTION_VIEW,
        SOURCE_CODE_URL.toUri(),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    context.startActivitySafely(sourceIntent)
}

private fun Context.startActivitySafely(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
}
