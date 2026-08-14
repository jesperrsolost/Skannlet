package com.jrs.skannlet.app

import android.content.Context
import android.net.Uri
import com.jrs.skannlet.BuildConfig
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jrs.skannlet.data.export.CollectionPrintDocument
import com.jrs.skannlet.data.repository.AppSnapshot
import com.jrs.skannlet.data.repository.DeletedCollection
import com.jrs.skannlet.data.repository.DeletedScanRow
import com.jrs.skannlet.data.repository.RepositoryResult
import com.jrs.skannlet.data.repository.ScannerRepository
import com.jrs.skannlet.data.repository.ScannerRepositoryContract
import com.jrs.skannlet.printer.LabelFormat
import com.jrs.skannlet.printer.LabelPrintData
import com.jrs.skannlet.printer.LabelPrinterManager
import com.jrs.skannlet.printer.LabelPrinterManagerContract
import com.jrs.skannlet.printer.LabelPrinterSettings
import com.jrs.skannlet.printer.PrinterEndpoint
import com.jrs.skannlet.update.AppUpdateManager
import com.jrs.skannlet.update.AppUpdateManagerContract
import com.jrs.skannlet.update.UpdateCheckResult
import com.jrs.skannlet.update.UpdateDownloadPhase
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
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
    private val appUpdateManager: AppUpdateManagerContract,
) : ViewModel() {
    private var selectedCollectionId: String? = null
    private val chargingSessionTracker = ChargingSessionTracker()
    private val actionMutex = Mutex()
    private val pendingDeletionUndos = mutableMapOf<String, PendingDeletionUndo>()
    private var updateDownloadJob: Job? = null
    private var isCancellingUpdateDownload = false

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
        resumePendingUpdateOrCheck()
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
        launchSerialized {
            try {
                val result = repository.addUser(name)
                refresh(messageOverride = result.message)
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                refresh(messageOverride = "Handlingen kunne ikke fullføres.")
            }
        }
    }

    fun setActiveUser(userId: String) {
        mutate { repository.setActiveUser(userId) }
    }

    fun onChargingStateChanged(isCharging: Boolean) {
        val selectionRequired = chargingSessionTracker.onChargingChanged(isCharging)
        _uiState.update { state ->
            state.copy(isDockUserSelectionRequired = selectionRequired)
        }
    }

    fun confirmDockUser(userId: String) {
        launchSerialized {
            try {
                val result = repository.setActiveUser(userId)
                refresh(messageOverride = result.message)
                if (_uiState.value.profile.activeUserId == userId) {
                    chargingSessionTracker.acknowledgeUserSelection()
                    _uiState.update { state ->
                        state.copy(isDockUserSelectionRequired = false)
                    }
                }
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                refresh(messageOverride = "Brukeren kunne ikke aktiveres.")
            }
        }
    }

    fun deleteUser(userId: String) {
        mutate { repository.deleteUser(userId) }
    }

    fun createCollection(name: String, isReturn: Boolean) {
        mutate { repository.createCollection(name, isReturn) }
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

    fun setCollectionReturnStatus(collectionId: String, isReturn: Boolean) {
        mutate { repository.setCollectionReturnStatus(collectionId, isReturn) }
    }

    fun lockCollection(collectionId: String) {
        mutate { repository.lockCollection(collectionId) }
    }

    fun unlockCollection(collectionId: String) {
        mutate { repository.unlockCollection(collectionId) }
    }

    fun deleteCollection(collectionId: String) {
        launchSerialized {
            try {
                val result = repository.deleteCollection(collectionId)
                val deleted = result.value
                if (deleted == null) {
                    refresh(messageOverride = result.message)
                    return@launchSerialized
                }
                if (selectedCollectionId == collectionId) selectedCollectionId = null
                refresh()
                offerDeletionUndo(
                    message = result.message ?: "Samlingen er slettet.",
                    pendingUndo = PendingDeletionUndo.Collection(deleted),
                )
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                refresh(messageOverride = "Handlingen kunne ikke fullføres.")
            }
        }
    }

    fun scanBarcode(barcode: String) {
        mutate { repository.scanBarcode(barcode) }
    }

    fun updateQuantity(rowId: String, quantity: Float) {
        mutate { repository.updateQuantity(rowId, quantity) }
    }

    fun updateScanRowComment(rowId: String, comment: String) {
        mutate { repository.updateScanRowComment(rowId, comment) }
    }

    fun deleteScanRow(rowId: String) {
        launchSerialized {
            try {
                val result = repository.deleteScanRow(rowId)
                val deleted = result.value
                if (deleted == null) {
                    refresh(messageOverride = result.message)
                    return@launchSerialized
                }
                refresh()
                offerDeletionUndo(
                    message = result.message ?: "Raden er slettet.",
                    pendingUndo = PendingDeletionUndo.Row(deleted),
                )
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                refresh(messageOverride = "Handlingen kunne ikke fullføres.")
            }
        }
    }

    fun resolveSnackbarAction(actionId: String, actionPerformed: Boolean) {
        launchSerialized {
            val pendingUndo = pendingDeletionUndos.remove(actionId) ?: return@launchSerialized
            if (!actionPerformed) return@launchSerialized

            try {
                val result = when (pendingUndo) {
                    is PendingDeletionUndo.Collection -> repository.restoreCollection(pendingUndo.deleted)
                    is PendingDeletionUndo.Row -> repository.restoreScanRow(pendingUndo.deleted)
                }
                refresh(messageOverride = result.message ?: "Slettingen ble angret.")
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                refresh(messageOverride = "Slettingen kunne ikke angres.")
            }
        }
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

    fun requestAppDataBackup(continueWithUpdate: Boolean = false) {
        val date = java.time.LocalDate.now().toString().replace("-", "")
        _effects.trySend(
            AppEffect.CreateBackupDocument(
                fileName = "Skannlet_backup_$date.zip",
                continueWithUpdate = continueWithUpdate,
            ),
        )
    }

    fun exportAppDataBackup(uri: Uri, continueWithUpdate: Boolean) {
        viewModelScope.launch {
            try {
                val result = repository.exportAppDataBackup(uri, BuildConfig.VERSION_NAME)
                _effects.send(AppEffect.ShowSnackbar(result.message ?: "Sikkerhetskopien er lagret."))
                if (continueWithUpdate) downloadAvailableUpdate()
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                _effects.send(AppEffect.ShowSnackbar(exception.localizedMessage ?: "Sikkerhetskopien kunne ikke lagres."))
            }
        }
    }

    fun restoreAppDataBackup(uri: Uri) {
        launchSerialized {
            try {
                val result = repository.restoreAppDataBackup(uri)
                selectedCollectionId = null
                refresh(messageOverride = result.message)
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                refresh(messageOverride = exception.localizedMessage ?: "Sikkerhetskopien kunne ikke gjenopprettes.")
            }
        }
    }

    fun checkForUpdates(manual: Boolean = true) {
        if (
            _uiState.value.appUpdate.status == AppUpdateStatus.Checking ||
            updateDownloadJob?.isActive == true ||
            isCancellingUpdateDownload
        ) {
            return
        }
        _uiState.update { state ->
            state.copy(
                appUpdate = state.appUpdate.copy(
                    status = AppUpdateStatus.Checking,
                    errorMessage = null,
                    isDialogVisible = true,
                ),
            )
        }
        viewModelScope.launch {
            try {
                when (val result = appUpdateManager.checkForUpdate(force = manual)) {
                    UpdateCheckResult.Skipped -> setUpdateIdle()
                    UpdateCheckResult.UpToDate -> {
                        setUpdateIdle()
                        if (manual) _effects.send(AppEffect.ShowSnackbar("Du har nyeste versjon."))
                    }
                    is UpdateCheckResult.Available -> {
                        _uiState.update { state ->
                            state.copy(
                                appUpdate = AppUpdateUiState(
                                    status = AppUpdateStatus.Available,
                                    release = result.release,
                                    isDialogVisible = true,
                                ),
                            )
                        }
                    }
                }
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                if (manual && _uiState.value.appUpdate.release != null) {
                    _uiState.update { state ->
                        state.copy(
                            appUpdate = state.appUpdate.copy(
                                status = AppUpdateStatus.Failed,
                                errorMessage = exception.localizedMessage ?: "Oppdateringssjekken feilet.",
                                isDialogVisible = true,
                            ),
                        )
                    }
                } else if (manual) {
                    setUpdateIdle()
                    _effects.send(
                        AppEffect.ShowSnackbar(
                            exception.localizedMessage ?: "Oppdateringssjekken feilet.",
                        ),
                    )
                } else {
                    setUpdateIdle()
                }
            }
        }
    }

    fun deferAvailableUpdate() {
        _uiState.value.appUpdate.release?.let(appUpdateManager::defer)
        setUpdateIdle()
    }

    fun dismissUpdateUi() {
        setUpdateIdle()
    }

    fun hideUpdateDownload() {
        _uiState.update { state ->
            if (state.appUpdate.status != AppUpdateStatus.Downloading) {
                state
            } else {
                state.copy(
                    appUpdate = state.appUpdate.copy(isDialogVisible = false),
                )
            }
        }
    }

    fun showUpdateUiOrCheck() {
        if (_uiState.value.appUpdate.status == AppUpdateStatus.Downloading) {
            _uiState.update { state ->
                state.copy(
                    appUpdate = state.appUpdate.copy(isDialogVisible = true),
                )
            }
        } else {
            checkForUpdates(manual = true)
        }
    }

    fun downloadAvailableUpdate() {
        val release = _uiState.value.appUpdate.release ?: return
        if (updateDownloadJob?.isActive == true || isCancellingUpdateDownload) return
        _uiState.update { state ->
            state.copy(
                appUpdate = state.appUpdate.copy(
                    status = AppUpdateStatus.Downloading,
                    progressPercent = 0,
                    downloadPhase = UpdateDownloadPhase.Pending,
                    downloadedFilePath = null,
                    errorMessage = null,
                    isDialogVisible = true,
                ),
            )
        }
        launchUpdateDownload {
            try {
                val file = appUpdateManager.downloadAndVerify(release) { progress ->
                    _uiState.update { state ->
                        state.copy(
                            appUpdate = state.appUpdate.copy(
                                progressPercent = progress.percent,
                                downloadPhase = progress.phase,
                            ),
                        )
                    }
                }
                _uiState.update { state ->
                    state.copy(
                        appUpdate = state.appUpdate.copy(
                            status = AppUpdateStatus.Ready,
                            progressPercent = 100,
                            downloadPhase = UpdateDownloadPhase.Verifying,
                            downloadedFilePath = file.absolutePath,
                            isDialogVisible = true,
                        ),
                    )
                }
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                _uiState.update { state ->
                    state.copy(
                        appUpdate = state.appUpdate.copy(
                            status = AppUpdateStatus.Failed,
                            errorMessage = exception.localizedMessage ?: "Oppdateringen kunne ikke lastes ned.",
                            isDialogVisible = true,
                        ),
                    )
                }
            }
        }
    }

    fun cancelUpdateDownload() {
        val updateState = _uiState.value.appUpdate
        if (updateState.status != AppUpdateStatus.Downloading || isCancellingUpdateDownload) return
        isCancellingUpdateDownload = true
        val release = updateState.release
        val activeJob = updateDownloadJob
        updateDownloadJob = null
        viewModelScope.launch {
            var cleanupError: Exception? = null
            try {
                activeJob?.cancelAndJoin()
                appUpdateManager.cancelPendingDownload()
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                cleanupError = exception
            } finally {
                try {
                    release?.let(appUpdateManager::defer)
                } catch (exception: Exception) {
                    if (cleanupError == null) cleanupError = exception
                }
                setUpdateIdle()
                isCancellingUpdateDownload = false
            }
            _effects.send(
                AppEffect.ShowSnackbar(
                    cleanupError?.localizedMessage
                        ?: "Oppdateringsnedlastingen er avbrutt.",
                ),
            )
        }
    }

    fun installDownloadedUpdate() {
        val path = _uiState.value.appUpdate.downloadedFilePath ?: return
        val intent = if (appUpdateManager.canRequestPackageInstalls()) {
            appUpdateManager.installIntent(java.io.File(path))
        } else {
            appUpdateManager.unknownSourcesIntent()
        }
        _effects.trySend(AppEffect.LaunchIntent(intent))
    }

    private fun setUpdateIdle() {
        _uiState.update { state -> state.copy(appUpdate = AppUpdateUiState()) }
    }

    private fun resumePendingUpdateOrCheck() {
        val pendingRelease = appUpdateManager.pendingRelease()
        if (pendingRelease == null) {
            checkForUpdates(manual = false)
            return
        }
        _uiState.update { state ->
            state.copy(
                appUpdate = AppUpdateUiState(
                    status = AppUpdateStatus.Downloading,
                    release = pendingRelease,
                    progressPercent = 0,
                    downloadPhase = UpdateDownloadPhase.Pending,
                    isDialogVisible = true,
                ),
            )
        }
        var checkAfterResume = false
        launchUpdateDownload(
            onCompleted = {
                if (checkAfterResume) checkForUpdates(manual = false)
            },
        ) {
            try {
                val pending = appUpdateManager.resumePendingDownload { progress ->
                    _uiState.update { state ->
                        state.copy(
                            appUpdate = state.appUpdate.copy(
                                status = AppUpdateStatus.Downloading,
                                release = state.appUpdate.release ?: pendingRelease,
                                progressPercent = progress.percent,
                                downloadPhase = progress.phase,
                            ),
                        )
                    }
                }
                if (pending != null) {
                    val (release, file) = pending
                    _uiState.update { state ->
                        state.copy(
                            appUpdate = AppUpdateUiState(
                                status = AppUpdateStatus.Ready,
                                release = release,
                                progressPercent = 100,
                                downloadPhase = UpdateDownloadPhase.Verifying,
                                downloadedFilePath = file.absolutePath,
                                isDialogVisible = true,
                            ),
                        )
                    }
                } else {
                    setUpdateIdle()
                    checkAfterResume = true
                }
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                setUpdateIdle()
                checkAfterResume = true
            }
        }
    }

    private fun launchUpdateDownload(
        onCompleted: () -> Unit = {},
        block: suspend () -> Unit,
    ) {
        if (updateDownloadJob?.isActive == true || isCancellingUpdateDownload) return
        lateinit var job: Job
        job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                block()
            } finally {
                if (updateDownloadJob === job) {
                    updateDownloadJob = null
                    onCompleted()
                }
            }
        }
        updateDownloadJob = job
        job.start()
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

    private suspend fun offerDeletionUndo(
        message: String,
        pendingUndo: PendingDeletionUndo,
    ) {
        val actionId = UUID.randomUUID().toString()
        pendingDeletionUndos[actionId] = pendingUndo
        _effects.send(
            AppEffect.ShowSnackbar(
                message = message,
                actionLabel = "Angre",
                actionId = actionId,
            ),
        )
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
                refreshed.copy(
                    labelPrinter = current.labelPrinter,
                    appUpdate = current.appUpdate,
                    isDockUserSelectionRequired = current.isDockUserSelectionRequired,
                )
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
                isReturn = collection.isReturn,
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
                    creatorName = collection.creatorName,
                    isReturn = collection.isReturn,
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
                            comment = row.comment,
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
                        comment = row.comment,
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
                    appUpdateManager = AppUpdateManager(
                        context = context.applicationContext,
                        currentVersionName = BuildConfig.VERSION_NAME,
                        currentVersionCode = BuildConfig.VERSION_CODE.toLong(),
                    ),
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

private sealed interface PendingDeletionUndo {
    data class Collection(val deleted: DeletedCollection) : PendingDeletionUndo
    data class Row(val deleted: DeletedScanRow) : PendingDeletionUndo
}

private fun Throwable.printerErrorMessage(): String = when (this) {
    is java.net.SocketTimeoutException -> "Kunne ikke kontakte skriveren: tidsavbrudd."
    is java.net.ConnectException -> "Kunne ikke kontakte skriveren: tilkoblingen ble avvist."
    is java.net.NoRouteToHostException -> "Kunne ikke kontakte skriveren: ingen rute til IP-adressen."
    else -> localizedMessage ?: "Skriverhandlingen kunne ikke fullføres."
}
