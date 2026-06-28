package com.jrs.skannlet.app

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.jrs.skannlet.R
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.jrs.skannlet.ui.collections.CollectionDetailRoute
import com.jrs.skannlet.ui.collections.CollectionsRoute
import com.jrs.skannlet.ui.profile.ProfileRoute
import com.jrs.skannlet.ui.scan.ScanRoute

object AppRoutes {
    const val Collections = "collections"
    const val Scan = "scan"
    const val Profile = "profile"
    const val CollectionDetail = "collection/{collectionId}"

    fun collectionDetail(collectionId: String): String = "collection/${Uri.encode(collectionId)}"
}

data class TopLevelDestination(
    val route: String,
    val label: String,
    val iconResId: Int,
)

val TopLevelDestinations = listOf(
    TopLevelDestination(AppRoutes.Collections, "Prosjekter", R.drawable.box_24px),
    TopLevelDestination(AppRoutes.Scan, "Skanning", R.drawable.barcode_scanner_24px),
    TopLevelDestination(AppRoutes.Profile, "Profil", R.drawable.person_24px),
)

@Composable
fun AppNavGraph(
    navController: NavHostController,
    uiState: AppUiState,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = AppRoutes.Collections,
        modifier = modifier,
    ) {
        composable(AppRoutes.Collections) {
            CollectionsRoute(
                uiState = uiState.collections,
                activeUserName = uiState.profile.activeUserName,
                users = uiState.profile.users,
                onCreateCollection = viewModel::createCollection,
                onOpenCollection = { collectionId ->
                    viewModel.selectCollection(collectionId)
                    navController.navigate(AppRoutes.collectionDetail(collectionId))
                },
                onSetActiveCollection = viewModel::setActiveCollection,
                onUnlockCollection = viewModel::unlockCollection,
                onDeleteCollection = viewModel::deleteCollection,
                onSetActiveUser = viewModel::setActiveUser,
            )
        }
        composable(AppRoutes.Scan) {
            ScanRoute(
                uiState = uiState.scan,
                activeUserName = uiState.profile.activeUserName,
                users = uiState.profile.users,
                onScan = viewModel::scanBarcode,
                onUpdateQuantity = viewModel::updateQuantity,
                onDeleteRow = viewModel::deleteScanRow,
                onSelectCollection = {
                    navController.navigate(AppRoutes.Collections) {
                        launchSingleTop = true
                    }
                },
                onSetActiveUser = viewModel::setActiveUser,
            )
        }
        composable(AppRoutes.Profile) {
            ProfileRoute(
                uiState = uiState.profile,
                onAddUser = viewModel::addUser,
                onDeleteUser = viewModel::deleteUser,
                onSetActiveUser = viewModel::setActiveUser,
                onSetNextProjectNumber = viewModel::setNextCollectionProjectNumber,
                onImportProducts = viewModel::importProducts,
                onDeleteProducts = viewModel::deleteProducts,
            )
        }
        composable(
            route = AppRoutes.CollectionDetail,
            arguments = listOf(navArgument("collectionId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val collectionId = backStackEntry.arguments?.getString("collectionId").orEmpty()
            LaunchedEffect(collectionId) {
                viewModel.selectCollection(collectionId)
            }
            CollectionDetailRoute(
                uiState = uiState.collections,
                activeUserName = uiState.profile.activeUserName,
                onBack = { navController.popBackStack() },
                onSetActiveCollection = viewModel::setActiveCollection,
                onRenameCollection = viewModel::renameCollection,
                onUnlockCollection = viewModel::unlockCollection,
                onDeleteCollection = viewModel::deleteCollection,
                onUpdateQuantity = viewModel::updateQuantity,
                onDeleteScanRow = viewModel::deleteScanRow,
                onExportCollection = viewModel::exportCollection,
                onScanCollection = { collectionId ->
                    viewModel.setActiveCollection(collectionId)
                    navController.navigate(AppRoutes.Scan) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
    }
}

fun selectedTopLevelRoute(route: String?): String = when {
    route == AppRoutes.Scan -> AppRoutes.Scan
    route == AppRoutes.Profile -> AppRoutes.Profile
    else -> AppRoutes.Collections
}
