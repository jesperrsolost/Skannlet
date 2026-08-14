package com.jrs.skannlet.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jrs.skannlet.data.export.shareCollectionExport
import com.jrs.skannlet.ui.components.NameInputDialog
import com.jrs.skannlet.ui.components.UserPickerDialog

@Composable
fun OmfScannerApp(
    viewModel: AppViewModel = viewModel(
        factory = AppViewModel.Factory(LocalContext.current.applicationContext),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var backupContinuesWithUpdate by rememberSaveable { mutableStateOf(false) }
    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        uri?.let { viewModel.exportAppDataBackup(it, backupContinuesWithUpdate) }
        backupContinuesWithUpdate = false
    }
    val showDockUserPicker = !uiState.isLoading &&
        !uiState.needsUser &&
        uiState.profile.users.isNotEmpty() &&
        uiState.isDockUserSelectionRequired

    ChargingDockObserver(viewModel::onChargingStateChanged)

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearMessage()
    }

    LaunchedEffect(context) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AppEffect.ShowSnackbar -> {
                    val result = snackbarHostState.showSnackbar(
                        message = effect.message,
                        actionLabel = effect.actionLabel,
                        duration = if (effect.actionLabel != null) {
                            SnackbarDuration.Long
                        } else {
                            SnackbarDuration.Short
                        },
                    )
                    effect.actionId?.let { actionId ->
                        viewModel.resolveSnackbarAction(
                            actionId = actionId,
                            actionPerformed = result == SnackbarResult.ActionPerformed,
                        )
                    }
                }
                is AppEffect.ShareCollectionExport -> shareCollectionExport(
                    context = context,
                    csvUri = effect.csvUri,
                    csvFileName = effect.csvFileName,
                    printDocument = effect.printDocument,
                    printFileName = effect.printFileName,
                )
                is AppEffect.CreateBackupDocument -> {
                    backupContinuesWithUpdate = effect.continueWithUpdate
                    backupLauncher.launch(effect.fileName)
                }
                is AppEffect.LaunchIntent -> runCatching {
                    context.startActivity(effect.intent)
                }.onFailure {
                    snackbarHostState.showSnackbar("Kunne ikke åpne Android-installasjonen.")
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val selectedRoute = selectedTopLevelRoute(backStackEntry?.destination?.route)
            NavigationBar {
                TopLevelDestinations.forEach { destination ->
                    NavigationBarItem(
                        selected = destination.route == selectedRoute,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                painter = painterResource(destination.iconResId),
                                contentDescription = destination.label,
                            )
                        },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(innerPadding))
        } else {
            AppNavGraph(
                navController = navController,
                uiState = uiState,
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }

    if (!uiState.isLoading && uiState.needsUser) {
        NameInputDialog(
            title = "Første oppstart",
            label = "Navn",
            confirmText = "Lagre bruker",
            onConfirm = viewModel::addUser,
        )
    } else if (showDockUserPicker) {
        UserPickerDialog(
            users = uiState.profile.users,
            title = "Velg aktiv bruker",
            allowActiveUserSelection = true,
            dismissible = false,
            onSelectUser = viewModel::confirmDockUser,
            onDismiss = {},
        )
    }

    if (
        !uiState.isLoading &&
        !uiState.needsUser &&
        !showDockUserPicker &&
        uiState.appUpdate.isDialogVisible
    ) {
        AppUpdateDialog(
            state = uiState.appUpdate,
            onBackupAndUpdate = { viewModel.requestAppDataBackup(continueWithUpdate = true) },
            onUpdate = viewModel::downloadAvailableUpdate,
            onInstall = viewModel::installDownloadedUpdate,
            onLater = viewModel::deferAvailableUpdate,
            onDismiss = viewModel::dismissUpdateUi,
            onHideDownload = viewModel::hideUpdateDownload,
            onCancelDownload = viewModel::cancelUpdateDownload,
        )
    }
}
