package com.jrs.skannlet.app

import android.net.Uri
import android.content.Intent
import com.jrs.skannlet.data.export.CollectionPrintDocument
import com.jrs.skannlet.printer.LabelPrinterSettings
import com.jrs.skannlet.update.UpdateDownloadPhase
import com.jrs.skannlet.update.UpdateRelease

data class AppUiState(
    val isLoading: Boolean = true,
    val needsUser: Boolean = false,
    val message: String? = null,
    val collections: CollectionsUiState = CollectionsUiState(),
    val scan: ScanUiState = ScanUiState(),
    val profile: ProfileUiState = ProfileUiState(),
    val labelPrinter: LabelPrinterUiState = LabelPrinterUiState(),
    val appUpdate: AppUpdateUiState = AppUpdateUiState(),
    val isDockUserSelectionRequired: Boolean = false,
)

enum class AppUpdateStatus {
    Idle,
    Checking,
    Available,
    Downloading,
    Ready,
    Failed,
}

data class AppUpdateUiState(
    val status: AppUpdateStatus = AppUpdateStatus.Idle,
    val release: UpdateRelease? = null,
    val progressPercent: Int? = null,
    val downloadPhase: UpdateDownloadPhase? = null,
    val downloadedFilePath: String? = null,
    val errorMessage: String? = null,
    val isDialogVisible: Boolean = true,
)

data class LabelPrinterUiState(
    val settings: LabelPrinterSettings = LabelPrinterSettings(),
    val isTesting: Boolean = false,
    val printingRowId: String? = null,
)

data class CollectionsUiState(
    val items: List<CollectionListItemUiState> = emptyList(),
    val activeCollectionId: String? = null,
    val detail: CollectionDetailUiState? = null,
)

data class CollectionListItemUiState(
    val id: String,
    val projectNumber: Int,
    val name: String,
    val isReturn: Boolean = false,
    val scanCount: Int,
    val updatedAt: Long,
    val isActive: Boolean,
    val isLocked: Boolean,
)

data class CollectionDetailUiState(
    val id: String,
    val projectNumber: Int,
    val name: String,
    val creatorName: String? = null,
    val isReturn: Boolean = false,
    val scanCount: Int,
    val updatedAt: Long,
    val isActive: Boolean,
    val isLocked: Boolean,
    val rows: List<ScanRowUiState>,
)

data class ScanRowUiState(
    val id: String,
    val barcode: String,
    val productName: String,
    val quantity: Float,
    val quantityLocked: Boolean,
    val createdAt: Long,
    val comment: String = "",
)

data class ScanUiState(
    val activeCollectionName: String? = null,
    val status: String = "Klar til skanning",
    val lastMessage: String? = null,
    val hasActiveCollection: Boolean = false,
    val latestScannedRow: ScanRowUiState? = null,
)

data class ProfileUiState(
    val users: List<UserUiState> = emptyList(),
    val activeUserId: String? = null,
    val activeUserName: String? = null,
    val productCount: Int = 0,
    val nextCollectionProjectNumber: Int = 1,
)

data class UserUiState(
    val id: String,
    val name: String,
    val isActive: Boolean,
)

sealed interface AppEffect {
    data class ShowSnackbar(
        val message: String,
        val actionLabel: String? = null,
        val actionId: String? = null,
    ) : AppEffect

    data class ShareCollectionExport(
        val csvUri: Uri,
        val csvFileName: String,
        val printDocument: CollectionPrintDocument,
        val printFileName: String,
    ) : AppEffect

    data class CreateBackupDocument(
        val fileName: String,
        val continueWithUpdate: Boolean,
    ) : AppEffect

    data class LaunchIntent(val intent: Intent) : AppEffect
}
