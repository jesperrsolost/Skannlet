package com.jrs.skannlet.app

import android.net.Uri

data class AppUiState(
    val isLoading: Boolean = true,
    val needsUser: Boolean = false,
    val message: String? = null,
    val collections: CollectionsUiState = CollectionsUiState(),
    val scan: ScanUiState = ScanUiState(),
    val profile: ProfileUiState = ProfileUiState(),
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
    val scanCount: Int,
    val updatedAt: Long,
    val isActive: Boolean,
    val isLocked: Boolean,
)

data class CollectionDetailUiState(
    val id: String,
    val projectNumber: Int,
    val name: String,
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
    val quantity: Int,
    val quantityLocked: Boolean,
    val createdAt: Long,
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
    data class ShareCollectionExport(
        val csvUri: Uri,
        val csvFileName: String,
        val printDocument: CollectionPrintDocument,
        val printFileName: String,
    ) : AppEffect
}

data class CollectionPrintDocument(
    val title: String,
    val metaText: String,
    val rows: List<CollectionPrintRow>,
)

data class CollectionPrintRow(
    val quantity: String,
    val barcode: String,
    val productName: String,
    val createdAt: String,
)
