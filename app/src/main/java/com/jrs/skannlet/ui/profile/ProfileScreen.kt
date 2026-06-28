package com.jrs.skannlet.ui.profile

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.jrs.skannlet.R
import com.jrs.skannlet.app.ProfileUiState
import com.jrs.skannlet.app.UserUiState
import com.jrs.skannlet.ui.components.NameInputDialog

private const val TEAMVIEWER_QUICKSUPPORT_PACKAGE = "com.teamviewer.quicksupport.market"
private const val SOURCE_CODE_URL = "https://github.com/jesperrsolost/Skannlet"

@Composable
fun ProfileRoute(
    uiState: ProfileUiState,
    onAddUser: (String) -> Unit,
    onDeleteUser: (String) -> Unit,
    onSetActiveUser: (String) -> Unit,
    onSetNextProjectNumber: (Int) -> Unit,
    onImportProducts: (Uri) -> Unit,
    onDeleteProducts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteUserDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteProductsDialog by rememberSaveable { mutableStateOf(false) }
    var showProjectNumberDialog by rememberSaveable { mutableStateOf(false) }
    var showAboutScreen by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val activeUserId = uiState.activeUserId
    val activeUserName = uiState.activeUserName
    val productImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(onImportProducts)
    }

    if (showAboutScreen) {
        BackHandler { showAboutScreen = false }
        AboutApplicationScreen(
            onBackClick = { showAboutScreen = false },
            modifier = modifier,
        )
    } else {
        ProfileScreen(
            uiState = uiState,
            onAddUserClick = { showAddDialog = true },
            onDeleteUserClick = { showDeleteUserDialog = true },
            onSupportClick = { openTeamViewerQuickSupport(context) },
            onAboutClick = { showAboutScreen = true },
            onSetProjectNumberClick = { showProjectNumberDialog = true },
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
            onDeleteProductsClick = { showDeleteProductsDialog = true },
            modifier = modifier,
        )
    }

    if (showProjectNumberDialog) {
        ProjectNumberDialog(
            initialProjectNumber = uiState.nextCollectionProjectNumber,
            onConfirm = { projectNumber ->
                showProjectNumberDialog = false
                onSetNextProjectNumber(projectNumber)
            },
            onDismiss = { showProjectNumberDialog = false },
        )
    }

    if (showAddDialog) {
        NameInputDialog(
            title = "Ny bruker",
            label = "Navn",
            confirmText = "Legg til",
            onConfirm = { name ->
                showAddDialog = false
                onAddUser(name)
            },
            onDismiss = { showAddDialog = false },
        )
    }

    if (showDeleteUserDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteUserDialog = false },
            title = { Text("Slett bruker") },
            text = { Text("Slett ${activeUserName ?: "aktiv bruker"} lokalt? Prosjekter og skanninger beholdes.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    onClick = {
                        val userId = activeUserId ?: return@Button
                        showDeleteUserDialog = false
                        onDeleteUser(userId)
                    },
                ) {
                    Text("Slett")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteUserDialog = false }) {
                    Text("Avbryt")
                }
            },
        )
    }

    if (showDeleteProductsDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteProductsDialog = false },
            title = { Text("Slett produktliste") },
            text = { Text("Dette sletter den lokale produktlisten og setter eksisterende produktnavn til Ukjent vare.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteProductsDialog = false
                        onDeleteProducts()
                    },
                ) {
                    Text("Slett")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteProductsDialog = false }) {
                    Text("Avbryt")
                }
            },
        )
    }
}

@Composable
fun ProfileScreen(
    uiState: ProfileUiState,
    onAddUserClick: () -> Unit,
    onDeleteUserClick: () -> Unit,
    onSupportClick: () -> Unit,
    onAboutClick: () -> Unit,
    onSetProjectNumberClick: () -> Unit,
    onSetActiveUser: (String) -> Unit,
    onImportProductsClick: () -> Unit,
    onDeleteProductsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Profil",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            Image(
                painter = painterResource(R.drawable.omflogo),
                contentDescription = "OM Fjeld",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(80.dp)
                    .height(32.dp),
            )
            Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert_24px),
                        contentDescription = "Flere valg",
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Ny bruker") },
                        onClick = {
                            menuExpanded = false
                            onAddUserClick()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Slett bruker") },
                        enabled = uiState.activeUserId != null,
                        onClick = {
                            menuExpanded = false
                            onDeleteUserClick()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Sett løpenummer") },
                        onClick = {
                            menuExpanded = false
                            onSetProjectNumberClick()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Support") },
                        onClick = {
                            menuExpanded = false
                            onSupportClick()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Om applikasjonen") },
                        onClick = {
                            menuExpanded = false
                            onAboutClick()
                        },
                    )
                }
            }
        }

        ActiveUserCard(activeUserName = uiState.activeUserName)
        ProductImportCard(
            productCount = uiState.productCount,
            onImportProductsClick = onImportProductsClick,
            onDeleteProductsClick = onDeleteProductsClick,
        )

        Text(
            text = "Brukere",
            style = MaterialTheme.typography.titleMedium,
        )
        if (uiState.users.isEmpty()) {
            Text("Ingen bruker er opprettet.")
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(uiState.users, key = { it.id }) { user ->
                    UserItem(
                        user = user,
                        onSetActive = { onSetActiveUser(user.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectNumberDialog(
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
                    value = nextValue.filter { it.isDigit() }.take(9)
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

private fun Context.appVersionName(): String {
    val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0)
    }
    return packageInfo.versionName ?: "Ukjent"
}

@Composable
private fun AboutApplicationScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appName = stringResource(R.string.app_name)
    val versionName = remember(context) { context.appVersionName() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back_24px),
                    contentDescription = "Tilbake",
                )
            }
            Text(
                text = "Om applikasjonen",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Image(
            painter = painterResource(R.drawable.omflogo),
            contentDescription = "OM Fjeld",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .width(160.dp)
                .height(64.dp),
        )

        Text(
            text = appName,
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "Versjon: $versionName (26.06.2026)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Lisens: Apache 2.0",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "kildekode",
                style = MaterialTheme.typography.bodyMedium.copy(
                    textDecoration = TextDecoration.Underline,
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(
                    onClickLabel = "Åpne kildekode",
                    role = Role.Button,
                    onClick = { openSourceCode(context) },
                ),
            )
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Lokal skanning for prosjekter",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Appen brukes til å registrere varer med strekkode, organisere skanninger per prosjekt og eksportere prosjektdata til CSV og PDF.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Data",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Brukere, produktliste og prosjekter lagres lokalt på enheten.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Text(
            text = "Utviklet for Ø. M. Fjeld Prosjektservice av Jesper Ruud Soløst",
            style = MaterialTheme.typography.labelSmall,
        )

    }
}

@Composable
private fun ProductImportCard(
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
private fun ActiveUserCard(
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
private fun UserItem(
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
                AssistChip(onClick = {}, label = { Text("Aktiv") })
            } else {
                OutlinedButton(onClick = onSetActive) {
                    Text("Bytt")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    ProfileScreen(
        uiState = ProfileUiState(
            users = listOf(
                UserUiState(id = "1", name = "Ola Nordmann", isActive = true),
                UserUiState(id = "2", name = "Kari Nordmann", isActive = false),
            ),
            activeUserId = "1",
            activeUserName = "Ola Nordmann",
            productCount = 2,
            nextCollectionProjectNumber = 3,
        ),
        onAddUserClick = {},
        onDeleteUserClick = {},
        onSupportClick = {},
        onAboutClick = {},
        onSetProjectNumberClick = {},
        onSetActiveUser = {},
        onImportProductsClick = {},
        onDeleteProductsClick = {},
    )
}
