package com.jrs.skannlet.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jrs.skannlet.app.LabelPrinterUiState
import com.jrs.skannlet.app.ProfileUiState
import com.jrs.skannlet.app.UserUiState
import com.jrs.skannlet.ui.profile.components.ActiveUserCard
import com.jrs.skannlet.ui.profile.components.LabelPrinterCard
import com.jrs.skannlet.ui.profile.components.ProductImportCard
import com.jrs.skannlet.ui.profile.components.ProfileHeader
import com.jrs.skannlet.ui.profile.components.ProfileUserItem

@Composable
fun ProfileScreen(
    uiState: ProfileUiState,
    labelPrinterState: LabelPrinterUiState,
    onAddUserClick: () -> Unit,
    onDeleteUserClick: () -> Unit,
    onSupportClick: () -> Unit,
    onAboutClick: () -> Unit,
    onConfigurePrinterClick: () -> Unit,
    onSetProjectNumberClick: () -> Unit,
    onSetActiveUser: (String) -> Unit,
    onImportProductsClick: () -> Unit,
    onDeleteProductsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ProfileHeader(
                canDeleteUser = uiState.activeUserId != null,
                onAddUserClick = onAddUserClick,
                onDeleteUserClick = onDeleteUserClick,
                onSetProjectNumberClick = onSetProjectNumberClick,
                onSupportClick = onSupportClick,
                onAboutClick = onAboutClick,
            )
        }
        item { ActiveUserCard(activeUserName = uiState.activeUserName) }
        item {
            ProductImportCard(
                productCount = uiState.productCount,
                onImportProductsClick = onImportProductsClick,
                onDeleteProductsClick = onDeleteProductsClick,
            )
        }
        item {
            LabelPrinterCard(
                state = labelPrinterState,
                onConfigureClick = onConfigurePrinterClick,
            )
        }
        item {
            Text(
                text = "Brukere",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (uiState.users.isEmpty()) {
            item { Text("Ingen bruker er opprettet.") }
        } else {
            items(uiState.users, key = UserUiState::id) { user ->
                ProfileUserItem(
                    user = user,
                    onSetActive = { onSetActiveUser(user.id) },
                )
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
        labelPrinterState = LabelPrinterUiState(),
        onAddUserClick = {},
        onDeleteUserClick = {},
        onSupportClick = {},
        onAboutClick = {},
        onConfigurePrinterClick = {},
        onSetProjectNumberClick = {},
        onSetActiveUser = {},
        onImportProductsClick = {},
        onDeleteProductsClick = {},
    )
}
