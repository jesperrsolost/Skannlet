package com.jrs.skannlet.app

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jrs.skannlet.data.repository.AppSnapshot
import com.jrs.skannlet.data.repository.RepositoryResult
import com.jrs.skannlet.data.repository.ScannerRepositoryContract
import com.jrs.skannlet.data.repository.ScannerRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AppViewModel(
    private val repository: ScannerRepositoryContract,
) : ViewModel() {
    private var selectedCollectionId: String? = null
    private val actionMutex = Mutex()

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AppEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<AppEffect> = _effects.asSharedFlow()

    init {
        launchSerialized {
            refresh()
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun selectCollection(collectionId: String) {
        launchSerialized {
            selectedCollectionId = collectionId
            refresh()
        }
    }

    fun addUser(name: String) {
        mutate { repository.addUser(name) }
    }

    fun setActiveUser(userId: String) {
        mutate { repository.setActiveUser(userId) }
    }

    fun deleteUser(userId: String) {
        mutate { repository.deleteUser(userId) }
    }

    fun createCollection(name: String) {
        mutate { repository.createCollection(name) }
    }

    fun setNextCollectionProjectNumber(projectNumber: Int) {
        mutate { repository.setNextCollectionProjectNumber(projectNumber) }
    }

    fun setActiveCollection(collectionId: String) {
        mutate { repository.setActiveCollection(collectionId) }
    }

    fun renameCollection(collectionId: String, name: String) {
        mutate { repository.renameCollection(collectionId, name) }
    }

    fun unlockCollection(collectionId: String) {
        mutate { repository.unlockCollection(collectionId) }
    }

    fun deleteCollection(collectionId: String) {
        launchSerialized {
            if (selectedCollectionId == collectionId) selectedCollectionId = null
            runRepositoryCall { repository.deleteCollection(collectionId) }
        }
    }

    fun scanBarcode(barcode: String) {
        mutate { repository.scanBarcode(barcode) }
    }

    fun updateQuantity(rowId: String, quantity: Int) {
        mutate { repository.updateQuantity(rowId, quantity) }
    }

    fun deleteScanRow(rowId: String) {
        mutate { repository.deleteScanRow(rowId) }
    }

    fun exportCollection(
        collectionId: String,
        printDocument: CollectionPrintDocument,
        printFileName: String,
    ) {
        launchSerialized {
            try {
                val result = repository.exportCollection(collectionId)
                result.value?.let { exported ->
                    _effects.emit(
                        AppEffect.ShareCollectionExport(
                            csvUri = exported.uri,
                            csvFileName = exported.fileName,
                            printDocument = printDocument,
                            printFileName = printFileName,
                        ),
                    )
                }
                refresh(messageOverride = result.message)
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                refresh(messageOverride = "Eksport feilet.")
            }
        }
    }

    fun importProducts(uri: Uri) {
        mutate { repository.importProducts(uri) }
    }

    fun deleteProducts() {
        mutate { repository.deleteProducts() }
    }

    private fun <T> mutate(block: suspend () -> RepositoryResult<T>) {
        launchSerialized {
            runRepositoryCall(block)
        }
    }

    private fun launchSerialized(block: suspend () -> Unit) {
        viewModelScope.launch {
            actionMutex.withLock {
                block()
            }
        }
    }

    private suspend fun <T> runRepositoryCall(block: suspend () -> RepositoryResult<T>) {
        try {
            val result = block()
            refresh(messageOverride = result.message)
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            refresh(messageOverride = "Handlingen kunne ikke fullføres.")
        }
    }

    private suspend fun refresh(messageOverride: String? = null) {
        try {
            val snapshotResult = repository.loadSnapshot()
            _uiState.value = snapshotResult.value.toUiState(
                selectedCollectionId = selectedCollectionId,
                message = messageOverride ?: snapshotResult.message,
            )
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            _uiState.update {
                it.copy(
                    isLoading = false,
                    message = messageOverride ?: "Lokale data kunne ikke leses.",
                )
            }
        }
    }

    private fun AppSnapshot.toUiState(
        selectedCollectionId: String?,
        message: String?,
    ): AppUiState {
        val rowsByCollection = rows.groupBy { it.collectionId }
        val activeCollection = collections.firstOrNull { it.id == activeCollectionId }
        val activeCollectionRows = activeCollectionId?.let { rowsByCollection[it].orEmpty() }.orEmpty()
        val activeUser = users.firstOrNull { it.id == activeUserId }
        val collectionItems = collections.map { collection ->
            CollectionListItemUiState(
                id = collection.id,
                projectNumber = collection.projectNumber,
                name = collection.name,
                scanCount = rowsByCollection[collection.id].orEmpty().size,
                updatedAt = collection.updatedAt,
                isActive = collection.id == activeCollectionId,
                isLocked = collection.isLocked,
            )
        }
        val selectedCollection = selectedCollectionId?.let { collectionId ->
            collections.firstOrNull { it.id == collectionId }?.let { collection ->
                CollectionDetailUiState(
                    id = collection.id,
                    projectNumber = collection.projectNumber,
                    name = collection.name,
                    scanCount = rowsByCollection[collection.id].orEmpty().size,
                    updatedAt = collection.updatedAt,
                    isActive = collection.id == activeCollectionId,
                    isLocked = collection.isLocked,
                    rows = rowsByCollection[collection.id].orEmpty().map { row ->
                        ScanRowUiState(
                            id = row.id,
                            barcode = row.barcode,
                            productName = row.productName,
                            quantity = row.quantity,
                            quantityLocked = row.quantityLocked,
                            createdAt = row.createdAt,
                        )
                    },
                )
            }
        }

        return AppUiState(
            isLoading = false,
            needsUser = users.isEmpty(),
            message = message,
            collections = CollectionsUiState(
                items = collectionItems,
                activeCollectionId = activeCollectionId,
                detail = selectedCollection,
            ),
            scan = ScanUiState(
                activeCollectionName = activeCollection?.name,
                status = "Klar til skanning",
                lastMessage = message?.takeIf { it.startsWith("Registrert:") },
                hasActiveCollection = activeCollection != null,
                latestScannedRow = activeCollectionRows.firstOrNull()?.let { row ->
                    ScanRowUiState(
                        id = row.id,
                        barcode = row.barcode,
                        productName = row.productName,
                        quantity = row.quantity,
                        quantityLocked = row.quantityLocked,
                        createdAt = row.createdAt,
                    )
                },
            ),
            profile = ProfileUiState(
                users = users.map { user ->
                    UserUiState(
                        id = user.id,
                        name = user.name,
                        isActive = user.id == activeUserId,
                    )
                },
                activeUserId = activeUserId,
                activeUserName = activeUser?.name,
                productCount = products.size,
                nextCollectionProjectNumber = nextCollectionProjectNumber,
            ),
        )
    }

    class Factory(
        private val context: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
                return AppViewModel(ScannerRepository(context.applicationContext)) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
