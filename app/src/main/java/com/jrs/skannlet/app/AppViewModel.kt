package com.jrs.skannlet.app

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jrs.skannlet.data.export.CollectionPrintDocument
import com.jrs.skannlet.data.repository.AppSnapshot
import com.jrs.skannlet.data.repository.RepositoryResult
import com.jrs.skannlet.data.repository.ScannerRepository
import com.jrs.skannlet.data.repository.ScannerRepositoryContract
import com.jrs.skannlet.printer.LabelFormat
import com.jrs.skannlet.printer.LabelPrintData
import com.jrs.skannlet.printer.LabelPrinterManager
import com.jrs.skannlet.printer.LabelPrinterManagerContract
import com.jrs.skannlet.printer.LabelPrinterSettings
import com.jrs.skannlet.printer.PrinterEndpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AppViewModel(
    private val repository: ScannerRepositoryContract,
    private val labelPrinterManager: LabelPrinterManagerContract,
) : ViewModel() {
    private var selectedCollectionId: String? = null
    private val actionMutex = Mutex()

    private val _uiState = MutableStateFlow(
        AppUiState(
            labelPrinter = LabelPrinterUiState(settings = labelPrinterManager.loadSettings()),
        ),
    )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _effects = Channel<AppEffect>(capacity = Channel.BUFFERED)
    val effects: Flow<AppEffect> = _effects.receiveAsFlow()

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
                    _effects.send(
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

    fun savePrinterEndpoint(ipAddress: String, port: Int) {
        updatePrinterSettings("Skriveroppsettet er lagret.") {
            labelPrinterManager.saveEndpoint(PrinterEndpoint(ipAddress, port))
        }
    }

    fun selectLabelFormat(formatId: String) {
        updatePrinterSettings {
            val settings = labelPrinterManager.selectFormat(formatId)
            val formatName = settings.selectedFormat.name
            settings to "Valgt etikettformat: $formatName."
        }
    }

    fun saveLabelFormat(format: LabelFormat) {
        updatePrinterSettings {
            val settings = labelPrinterManager.saveCustomFormat(format)
            settings to "Etikettformatet ${format.name.trim()} er lagret."
        }
    }

    fun deleteLabelFormat(formatId: String) {
        updatePrinterSettings("Etikettformatet er slettet.") {
            labelPrinterManager.deleteCustomFormat(formatId)
        }
    }

    fun testPrinterConnection(ipAddress: String, port: Int) {
        if (_uiState.value.labelPrinter.isTesting) return
        val endpoint = PrinterEndpoint(ipAddress.trim(), port)
        _uiState.update { state ->
            state.copy(labelPrinter = state.labelPrinter.copy(isTesting = true))
        }
        viewModelScope.launch {
            val result = labelPrinterManager.testConnection(endpoint)
            _uiState.update { state ->
                state.copy(labelPrinter = state.labelPrinter.copy(isTesting = false))
            }
            _effects.send(
                AppEffect.ShowSnackbar(
                    result.fold(
                        onSuccess = { connection ->
                            val model = connection.model?.let { " Modell: $it." }.orEmpty()
                            "Tilkoblingen virker.$model Skriverstatus: ${connection.status.description}."
                        },
                        onFailure = { it.printerErrorMessage() },
                    ),
                ),
            )
        }
    }

    fun printLabel(rowId: String) {
        val state = _uiState.value
        if (state.labelPrinter.printingRowId != null) return
        val row = state.collections.detail?.rows?.firstOrNull { it.id == rowId }
        if (row == null) {
            _uiState.update { it.copy(message = "Varen finnes ikke lenger.") }
            return
        }

        _uiState.update { current ->
            current.copy(labelPrinter = current.labelPrinter.copy(printingRowId = rowId))
        }
        viewModelScope.launch {
            val result = labelPrinterManager.printLabel(LabelPrintData(barcode = row.barcode))
            _uiState.update { current ->
                current.copy(labelPrinter = current.labelPrinter.copy(printingRowId = null))
            }
            _effects.send(
                AppEffect.ShowSnackbar(
                    result.fold(
                        onSuccess = { "Etiketten ble sendt til skriveren." },
                        onFailure = { it.printerErrorMessage() },
                    ),
                ),
            )
        }
    }

    private fun <T> mutate(block: suspend () -> RepositoryResult<T>) {
        launchSerialized {
            runRepositoryCall(block)
        }
    }

    private fun updatePrinterSettings(
        successMessage: String,
        update: () -> LabelPrinterSettings,
    ) {
        updatePrinterSettings { update() to successMessage }
    }

    private fun updatePrinterSettings(
        update: () -> Pair<LabelPrinterSettings, String>,
    ) {
        try {
            val (settings, message) = update()
            _uiState.update { state ->
                state.copy(
                    labelPrinter = state.labelPrinter.copy(settings = settings),
                )
            }
            _effects.trySend(AppEffect.ShowSnackbar(message))
        } catch (exception: Exception) {
            _effects.trySend(AppEffect.ShowSnackbar(exception.printerErrorMessage()))
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
            val refreshed = snapshotResult.value.toUiState(
                selectedCollectionId = selectedCollectionId,
                message = messageOverride ?: snapshotResult.message,
            )
            _uiState.update { current ->
                refreshed.copy(labelPrinter = current.labelPrinter)
            }
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
                return AppViewModel(
                    repository = ScannerRepository(context.applicationContext),
                    labelPrinterManager = LabelPrinterManager(context.applicationContext),
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

private fun Throwable.printerErrorMessage(): String = when (this) {
    is java.net.SocketTimeoutException -> "Kunne ikke kontakte skriveren: tidsavbrudd."
    is java.net.ConnectException -> "Kunne ikke kontakte skriveren: tilkoblingen ble avvist."
    is java.net.NoRouteToHostException -> "Kunne ikke kontakte skriveren: ingen rute til IP-adressen."
    else -> localizedMessage ?: "Skriverhandlingen kunne ikke fullføres."
}
