package com.jrs.skannlet.data.repository

import android.content.Context
import android.net.Uri
import com.jrs.skannlet.data.export.CsvExporter
import com.jrs.skannlet.data.export.ExportedCsv
import com.jrs.skannlet.data.importer.ProductCsvImportException
import com.jrs.skannlet.data.importer.ProductCsvImporter
import com.jrs.skannlet.data.model.AppUser
import com.jrs.skannlet.data.model.Product
import com.jrs.skannlet.data.model.ScanCollection
import com.jrs.skannlet.data.model.ScanRow
import com.jrs.skannlet.data.model.StoredAppState
import com.jrs.skannlet.data.storage.LocalJsonStorage
import com.jrs.skannlet.data.storage.StorageReadResult
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class AppSnapshot(
    val users: List<AppUser>,
    val collections: List<ScanCollection>,
    val rows: List<ScanRow>,
    val products: List<Product>,
    val activeUserId: String?,
    val activeCollectionId: String?,
    val nextCollectionProjectNumber: Int,
)

data class RepositoryResult<T>(
    val value: T,
    val message: String? = null,
)

data class DeletedScanRow(
    val row: ScanRow,
    val rowIndex: Int,
)

data class DeletedCollection(
    val collection: ScanCollection,
    val collectionIndex: Int,
    val rows: List<DeletedScanRow>,
    val activeCollectionIdBeforeDeletion: String?,
    val activeCollectionIdAfterDeletion: String?,
)

interface ScannerRepositoryContract {
    suspend fun loadSnapshot(): RepositoryResult<AppSnapshot>
    suspend fun addUser(name: String): RepositoryResult<Unit>
    suspend fun setActiveUser(userId: String): RepositoryResult<Unit>
    suspend fun deleteUser(userId: String): RepositoryResult<Unit>
    suspend fun createCollection(name: String, isReturn: Boolean): RepositoryResult<String?>
    suspend fun setNextCollectionProjectNumber(projectNumber: Int): RepositoryResult<Unit>
    suspend fun setActiveCollection(collectionId: String): RepositoryResult<Unit>
    suspend fun renameCollection(collectionId: String, name: String): RepositoryResult<Unit>
    suspend fun unlockCollection(collectionId: String): RepositoryResult<Unit>
    suspend fun deleteCollection(collectionId: String): RepositoryResult<DeletedCollection?>
    suspend fun restoreCollection(deleted: DeletedCollection): RepositoryResult<Unit>
    suspend fun scanBarcode(rawBarcode: String): RepositoryResult<Unit>
    suspend fun updateQuantity(rowId: String, quantity: Int): RepositoryResult<Unit>
    suspend fun deleteScanRow(rowId: String): RepositoryResult<DeletedScanRow?>
    suspend fun restoreScanRow(deleted: DeletedScanRow): RepositoryResult<Unit>
    suspend fun exportCollection(collectionId: String): RepositoryResult<ExportedCsv?>
    suspend fun importProducts(uri: Uri): RepositoryResult<Unit>
    suspend fun deleteProducts(): RepositoryResult<Unit>
}

class ScannerRepository(context: Context) : ScannerRepositoryContract {
    private val appContext = context.applicationContext
    private val storage = LocalJsonStorage(context)
    private val exporter = CsvExporter(context)
    private val mutex = Mutex()

    override suspend fun loadSnapshot(): RepositoryResult<AppSnapshot> = mutex.withLock {
        loadSnapshotLocked()
    }

    override suspend fun addUser(name: String): RepositoryResult<Unit> = mutex.withLock {
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) return@withLock RepositoryResult(Unit, "Skriv inn navn.")

        val read = storage.readAll()
        val now = System.currentTimeMillis()
        val user = AppUser(id = newId(), name = normalizedName, createdAt = now)
        storage.saveUsers(read.data.users + user)
        storage.saveAppState(
            read.data.appState.copy(activeUserId = user.id),
        )
        RepositoryResult(Unit, "Aktiv bruker: ${user.name}")
    }

    override suspend fun setActiveUser(userId: String): RepositoryResult<Unit> = mutex.withLock {
        val read = storage.readAll()
        val user = read.data.users.firstOrNull { it.id == userId }
            ?: return@withLock RepositoryResult(Unit, "Brukeren finnes ikke.")
        storage.saveAppState(read.data.appState.copy(activeUserId = user.id))
        RepositoryResult(Unit, "Aktiv bruker: ${user.name}")
    }

    override suspend fun deleteUser(userId: String): RepositoryResult<Unit> = mutex.withLock {
        val read = storage.readAll()
        val user = read.data.users.firstOrNull { it.id == userId }
            ?: return@withLock RepositoryResult(Unit, "Brukeren finnes ikke.")
        val remainingUsers = read.data.users.filterNot { it.id == userId }
        val nextActiveUserId = when (read.data.appState.activeUserId) {
            userId -> remainingUsers.firstOrNull()?.id
            else -> read.data.appState.activeUserId
        }

        storage.saveUsers(remainingUsers)
        storage.saveAppState(read.data.appState.copy(activeUserId = nextActiveUserId))

        RepositoryResult(Unit, "Bruker ${user.name} er slettet.")
    }

    override suspend fun createCollection(
        name: String,
        isReturn: Boolean,
    ): RepositoryResult<String?> = mutex.withLock {
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) return@withLock RepositoryResult(null, "Skriv inn navn på samling.")

        val read = readAllWithProjectNumbers()
        val creator = read.data.users.firstOrNull { user ->
            user.id == read.data.appState.activeUserId
        } ?: return@withLock RepositoryResult(null, "Velg bruker før du oppretter et prosjekt.")
        val now = System.currentTimeMillis()
        val projectNumber = read.data.appState.nextCollectionProjectNumber.coerceAtLeast(1)
        val collection = ScanCollection(
            id = newId(),
            projectNumber = projectNumber,
            name = normalizedName,
            createdAt = now,
            updatedAt = now,
            creatorName = creator.name,
            isReturn = isReturn,
        )
        storage.saveCollections(read.data.collections + collection)
        storage.saveAppState(
            read.data.appState.copy(
                activeCollectionId = collection.id,
                nextCollectionProjectNumber = projectNumber + 1,
            ),
        )
        RepositoryResult(collection.id, "Aktiv samling: ${collection.name}")
    }

    override suspend fun setNextCollectionProjectNumber(projectNumber: Int): RepositoryResult<Unit> = mutex.withLock {
        val read = readAllWithProjectNumbers()
        val minimumProjectNumber = collectionsNextProjectNumber(read.data.collections)
        val nextProjectNumber = maxOf(projectNumber, minimumProjectNumber, 1)
        storage.saveAppState(read.data.appState.copy(nextCollectionProjectNumber = nextProjectNumber))

        val message = if (nextProjectNumber == projectNumber) {
            "Løpenummer er satt til $nextProjectNumber."
        } else {
            "Løpenummer er satt til $nextProjectNumber. Lavere nummer er allerede brukt."
        }
        RepositoryResult(Unit, message)
    }

    override suspend fun setActiveCollection(collectionId: String): RepositoryResult<Unit> = mutex.withLock {
        val read = storage.readAll()
        val collection = read.data.collections.firstOrNull { it.id == collectionId }
            ?: return@withLock RepositoryResult(Unit, "Samlingen finnes ikke.")
        if (collection.isLocked) {
            return@withLock RepositoryResult(Unit, "Prosjektet er låst. Lås det opp før scanning.")
        }
        storage.saveAppState(read.data.appState.copy(activeCollectionId = collection.id))
        RepositoryResult(Unit, "Aktiv samling: ${collection.name}")
    }

    override suspend fun renameCollection(collectionId: String, name: String): RepositoryResult<Unit> = mutex.withLock {
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) return@withLock RepositoryResult(Unit, "Skriv inn nytt navn.")

        val read = storage.readAll()
        val collection = read.data.collections.firstOrNull { it.id == collectionId }
            ?: return@withLock RepositoryResult(Unit, "Samlingen finnes ikke.")
        if (collection.isLocked) {
            return@withLock RepositoryResult(Unit, "Prosjektet er låst. Lås det opp før endring.")
        }
        var found = false
        val now = System.currentTimeMillis()
        val updated = read.data.collections.map { collection ->
            if (collection.id == collectionId) {
                found = true
                collection.copy(name = normalizedName, updatedAt = now)
            } else {
                collection
            }
        }
        if (!found) return@withLock RepositoryResult(Unit, "Samlingen finnes ikke.")
        storage.saveCollections(updated)
        RepositoryResult(Unit, "Samlingen er endret.")
    }

    override suspend fun unlockCollection(collectionId: String): RepositoryResult<Unit> = mutex.withLock {
        val read = storage.readAll()
        var found = false
        val now = System.currentTimeMillis()
        val updated = read.data.collections.map { collection ->
            if (collection.id == collectionId) {
                found = true
                collection.copy(isLocked = false, updatedAt = now)
            } else {
                collection
            }
        }
        if (!found) return@withLock RepositoryResult(Unit, "Samlingen finnes ikke.")
        storage.saveCollections(updated)
        RepositoryResult(Unit, "Prosjektet er låst opp.")
    }

    override suspend fun deleteCollection(collectionId: String): RepositoryResult<DeletedCollection?> = mutex.withLock {
        val read = storage.readAll()
        val collectionIndex = read.data.collections.indexOfFirst { it.id == collectionId }
        if (collectionIndex < 0) return@withLock RepositoryResult(null, "Samlingen finnes ikke.")
        val collection = read.data.collections[collectionIndex]
        val updatedCollections = read.data.collections.filterNot { it.id == collectionId }
        val updatedRows = read.data.rows.filterNot { it.collectionId == collectionId }
        val nextActive = when {
            read.data.appState.activeCollectionId != collectionId -> read.data.appState.activeCollectionId
            else -> updatedCollections.firstOrNull { !it.isLocked }?.id
        }
        storage.saveCollections(updatedCollections)
        storage.saveRows(updatedRows)
        storage.saveAppState(read.data.appState.copy(activeCollectionId = nextActive))
        RepositoryResult(
            value = DeletedCollection(
                collection = collection,
                collectionIndex = collectionIndex,
                rows = read.data.rows.mapIndexedNotNull { index, row ->
                    row.takeIf { it.collectionId == collectionId }?.let {
                        DeletedScanRow(row = it, rowIndex = index)
                    }
                },
                activeCollectionIdBeforeDeletion = read.data.appState.activeCollectionId,
                activeCollectionIdAfterDeletion = nextActive,
            ),
            message = "Samlingen ${collection.name} er slettet.",
        )
    }

    override suspend fun restoreCollection(deleted: DeletedCollection): RepositoryResult<Unit> = mutex.withLock {
        val read = storage.readAll()
        if (read.data.collections.any { it.id == deleted.collection.id }) {
            return@withLock RepositoryResult(Unit, "Samlingen finnes allerede.")
        }

        storage.saveCollections(
            read.data.collections.insertAt(deleted.collectionIndex, deleted.collection),
        )
        var restoredRows = read.data.rows
        deleted.rows.sortedBy { it.rowIndex }.forEach { deletedRow ->
            if (restoredRows.none { it.id == deletedRow.row.id }) {
                restoredRows = restoredRows.insertAt(deletedRow.rowIndex, deletedRow.row)
            }
        }
        storage.saveRows(restoredRows)

        val currentActive = read.data.appState.activeCollectionId
        val restoredActive = if (
            deleted.activeCollectionIdBeforeDeletion == deleted.collection.id &&
            currentActive == deleted.activeCollectionIdAfterDeletion
        ) {
            deleted.collection.id
        } else {
            currentActive
        }
        storage.saveAppState(read.data.appState.copy(activeCollectionId = restoredActive))
        RepositoryResult(Unit, "Slettingen ble angret.")
    }

    override suspend fun scanBarcode(rawBarcode: String): RepositoryResult<Unit> = mutex.withLock {
        val barcode = rawBarcode.trim()
        if (barcode.isBlank()) return@withLock RepositoryResult(Unit)

        val read = storage.readAll()
        val activeCollectionId = read.data.appState.activeCollectionId
            ?: return@withLock RepositoryResult(Unit, "Velg en aktiv samling før skanning.")
        val collection = read.data.collections.firstOrNull { it.id == activeCollectionId }
            ?: return@withLock RepositoryResult(Unit, "Aktiv samling finnes ikke lenger.")
        if (collection.isLocked) {
            return@withLock RepositoryResult(Unit, "Prosjektet er låst. Lås det opp før scanning.")
        }

        val productName = read.data.products.firstOrNull { it.barcode == barcode }?.productName
            ?: UNKNOWN_PRODUCT
        val quantityLocked = barcode.isUniqueBarcode()
        val now = System.currentTimeMillis()

        val existingRow = read.data.rows.firstOrNull { row ->
            row.collectionId == activeCollectionId && row.barcode == barcode
        }
        if (quantityLocked && existingRow != null) {
            return@withLock RepositoryResult(Unit, "Unik vare er allerede skannet: $barcode")
        }
        if (!quantityLocked && existingRow != null) {
            val nextQuantity = existingRow.quantity + 1
            val updatedRows = read.data.rows.map { row ->
                if (row.id == existingRow.id) {
                    row.copy(
                        productName = productName,
                        quantity = nextQuantity,
                    )
                } else {
                    row
                }
            }
            val updatedCollections = read.data.collections.map {
                if (it.id == collection.id) it.copy(updatedAt = now) else it
            }

            storage.saveRows(updatedRows)
            storage.saveCollections(updatedCollections)
            return@withLock RepositoryResult(Unit, "Registrert: $barcode (antall: $nextQuantity)")
        }

        val row = ScanRow(
            id = newId(),
            collectionId = activeCollectionId,
            barcode = barcode,
            productName = productName,
            quantity = 1,
            quantityLocked = quantityLocked,
            createdAt = now,
        )
        val updatedCollections = read.data.collections.map {
            if (it.id == collection.id) it.copy(updatedAt = now) else it
        }
        storage.saveRows(read.data.rows + row)
        storage.saveCollections(updatedCollections)
        RepositoryResult(Unit, "Registrert: $barcode")
    }

    override suspend fun updateQuantity(rowId: String, quantity: Int): RepositoryResult<Unit> = mutex.withLock {
        val safeQuantity = quantity.coerceAtLeast(1)
        val read = storage.readAll()
        val collectionsById = read.data.collections.associateBy { it.id }
        var updatedCollectionId: String? = null
        var found = false
        val updatedRows = read.data.rows.map { row ->
            if (row.id == rowId) {
                found = true
                if (collectionsById[row.collectionId]?.isLocked == true) {
                    return@withLock RepositoryResult(Unit, "Prosjektet er låst. Lås det opp før endring.")
                }
                if (row.quantityLocked) {
                    row
                } else {
                    updatedCollectionId = row.collectionId
                    row.copy(quantity = safeQuantity)
                }
            } else {
                row
            }
        }
        if (!found) return@withLock RepositoryResult(Unit, "Raden finnes ikke.")
        storage.saveRows(updatedRows)

        if (updatedCollectionId != null) {
            val now = System.currentTimeMillis()
            storage.saveCollections(
                read.data.collections.map { collection ->
                    if (collection.id == updatedCollectionId) collection.copy(updatedAt = now) else collection
                },
            )
        }
        RepositoryResult(Unit)
    }

    override suspend fun deleteScanRow(rowId: String): RepositoryResult<DeletedScanRow?> = mutex.withLock {
        val read = storage.readAll()
        val rowIndex = read.data.rows.indexOfFirst { it.id == rowId }
        if (rowIndex < 0) return@withLock RepositoryResult(null, "Raden finnes ikke.")
        val row = read.data.rows[rowIndex]
        val collection = read.data.collections.firstOrNull { it.id == row.collectionId }
        if (collection?.isLocked == true) {
            return@withLock RepositoryResult(null, "Prosjektet er låst. Lås det opp før endring.")
        }
        val now = System.currentTimeMillis()

        storage.saveRows(read.data.rows.filterNot { it.id == rowId })
        storage.saveCollections(
            read.data.collections.map { collection ->
                if (collection.id == row.collectionId) collection.copy(updatedAt = now) else collection
            },
        )
        RepositoryResult(DeletedScanRow(row = row, rowIndex = rowIndex), "Raden er slettet.")
    }

    override suspend fun restoreScanRow(deleted: DeletedScanRow): RepositoryResult<Unit> = mutex.withLock {
        val read = storage.readAll()
        if (read.data.rows.any { it.id == deleted.row.id }) {
            return@withLock RepositoryResult(Unit, "Raden finnes allerede.")
        }
        if (read.data.collections.none { it.id == deleted.row.collectionId }) {
            return@withLock RepositoryResult(Unit, "Prosjektet finnes ikke lenger.")
        }

        storage.saveRows(read.data.rows.insertAt(deleted.rowIndex, deleted.row))
        val now = System.currentTimeMillis()
        storage.saveCollections(
            read.data.collections.map { collection ->
                if (collection.id == deleted.row.collectionId) collection.copy(updatedAt = now) else collection
            },
        )
        RepositoryResult(Unit, "Slettingen ble angret.")
    }

    override suspend fun exportCollection(collectionId: String): RepositoryResult<ExportedCsv?> = mutex.withLock {
        val snapshotResult = loadSnapshotLocked()
        val snapshot = snapshotResult.value
        val activeUser = snapshot.users.firstOrNull { it.id == snapshot.activeUserId }
            ?: return@withLock RepositoryResult(null, "Velg bruker før eksport.")
        val collection = snapshot.collections.firstOrNull { it.id == collectionId }
            ?: return@withLock RepositoryResult(null, "Samlingen finnes ikke.")
        val rows = snapshot.rows.filter { it.collectionId == collectionId }
        val exported = withContext(Dispatchers.IO) {
            exporter.exportCollection(activeUser, collection, rows)
        }
        val now = System.currentTimeMillis()
        val updatedCollections = snapshot.collections.map {
            if (it.id == collectionId) {
                it.copy(isLocked = true, updatedAt = now)
            } else {
                it
            }
        }
        val nextActiveCollectionId = when (snapshot.activeCollectionId) {
            collectionId -> updatedCollections.firstOrNull { !it.isLocked }?.id
            else -> snapshot.activeCollectionId
        }
        storage.saveCollections(updatedCollections)
        if (nextActiveCollectionId != snapshot.activeCollectionId) {
            storage.saveAppState(
                StoredAppState(
                    activeUserId = snapshot.activeUserId,
                    activeCollectionId = nextActiveCollectionId,
                    nextCollectionProjectNumber = snapshot.nextCollectionProjectNumber,
                ),
            )
        }
        RepositoryResult(exported, snapshotResult.message ?: "Prosjektet er eksportert og låst.")
    }

    override suspend fun importProducts(uri: Uri): RepositoryResult<Unit> = mutex.withLock {
        try {
            val importResult = withContext(Dispatchers.IO) {
                val csvBytes = appContext.contentResolver.openInputStream(uri)
                    ?.use { it.readBytes() }
                    ?: throw ProductCsvImportException("Produktlisten kunne ikke åpnes.")
                ProductCsvImporter.parse(csvBytes)
            }
            val read = storage.readAll()
            val productsByBarcode = importResult.products.associateBy { it.barcode }
            var updatedRowsCount = 0
            val updatedRows = read.data.rows.map { row ->
                val importedProduct = productsByBarcode[row.barcode]
                val productName = importedProduct?.productName ?: UNKNOWN_PRODUCT
                if (row.productName != productName) {
                    updatedRowsCount++
                    row.copy(productName = productName)
                } else {
                    row
                }
            }

            storage.saveProducts(importResult.products)
            if (updatedRowsCount > 0) {
                storage.saveRows(updatedRows)
            }

            val skippedText = if (importResult.skippedRows > 0) {
                " ${importResult.skippedRows} rader ble ignorert."
            } else {
                ""
            }
            RepositoryResult(
                Unit,
                "Importerte ${importResult.products.size} produkter. Oppdaterte $updatedRowsCount skannede rader.$skippedText",
            )
        } catch (exception: ProductCsvImportException) {
            RepositoryResult(Unit, exception.message ?: "Produktlisten kunne ikke importeres.")
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            RepositoryResult(Unit, "Produktlisten kunne ikke importeres.")
        }
    }

    override suspend fun deleteProducts(): RepositoryResult<Unit> = mutex.withLock {
        val read = storage.readAll()
        if (read.data.products.isEmpty()) {
            return@withLock RepositoryResult(Unit, "Ingen produktliste å slette.")
        }

        var updatedRowsCount = 0
        val updatedRows = read.data.rows.map { row ->
            if (row.productName != UNKNOWN_PRODUCT) {
                updatedRowsCount++
                row.copy(productName = UNKNOWN_PRODUCT)
            } else {
                row
            }
        }

        storage.saveProducts(emptyList())
        if (updatedRowsCount > 0) {
            storage.saveRows(updatedRows)
        }
        RepositoryResult(
            Unit,
            "Produktlisten er slettet. Oppdaterte $updatedRowsCount skannede rader.",
        )
    }

    private suspend fun loadSnapshotLocked(): RepositoryResult<AppSnapshot> {
        val read = readAllWithProjectNumbers()
        val users = read.data.users.sortedBy { it.createdAt }
        val collections = read.data.collections.sortedByDescending { it.updatedAt }
        val validActiveUserId = read.data.appState.activeUserId
            ?.takeIf { activeId -> users.any { it.id == activeId } }
            ?: users.firstOrNull()?.id
        val unlockedCollections = collections.filterNot { it.isLocked }
        val validActiveCollectionId = read.data.appState.activeCollectionId
            ?.takeIf { activeId -> unlockedCollections.any { it.id == activeId } }
            ?: unlockedCollections.firstOrNull()?.id
        if (
            validActiveUserId != read.data.appState.activeUserId ||
            validActiveCollectionId != read.data.appState.activeCollectionId
        ) {
            storage.saveAppState(
                read.data.appState.copy(
                    activeUserId = validActiveUserId,
                    activeCollectionId = validActiveCollectionId,
                ),
            )
        }

        return RepositoryResult(
            value = AppSnapshot(
                users = users,
                collections = collections,
                rows = read.data.rows.sortedByDescending { it.createdAt },
                products = read.data.products,
                activeUserId = validActiveUserId,
                activeCollectionId = validActiveCollectionId,
                nextCollectionProjectNumber = read.data.appState.nextCollectionProjectNumber,
            ),
            message = read.errors.firstOrNull(),
        )
    }

    private suspend fun readAllWithProjectNumbers(): StorageReadResult {
        val read = storage.readAll()
        val collections = read.data.collections.withStableProjectNumbers()
        val nextProjectNumber = maxOf(
            read.data.appState.nextCollectionProjectNumber,
            collectionsNextProjectNumber(collections),
            1,
        )
        val appState = read.data.appState.copy(nextCollectionProjectNumber = nextProjectNumber)

        if (collections != read.data.collections) {
            storage.saveCollections(collections)
        }
        if (appState != read.data.appState) {
            storage.saveAppState(appState)
        }

        return read.copy(
            data = read.data.copy(
                collections = collections,
                appState = appState,
            ),
        )
    }

    private fun List<ScanCollection>.withStableProjectNumbers(): List<ScanCollection> {
        if (isEmpty()) return this

        val usedNumbers = mutableSetOf<Int>()
        var nextCandidate = 1
        fun nextAvailableNumber(): Int {
            while (nextCandidate in usedNumbers) {
                nextCandidate++
            }
            val number = nextCandidate
            usedNumbers += number
            nextCandidate++
            return number
        }

        val projectNumbersById = mutableMapOf<String, Int>()
        sortedWith(compareBy<ScanCollection> { it.createdAt }.thenBy { it.id }).forEach { collection ->
            val projectNumber = if (collection.projectNumber > 0 && usedNumbers.add(collection.projectNumber)) {
                collection.projectNumber
            } else {
                nextAvailableNumber()
            }
            projectNumbersById[collection.id] = projectNumber
        }

        return map { collection ->
            val projectNumber = projectNumbersById.getValue(collection.id)
            if (collection.projectNumber == projectNumber) {
                collection
            } else {
                collection.copy(projectNumber = projectNumber)
            }
        }
    }

    private fun collectionsNextProjectNumber(collections: List<ScanCollection>): Int =
        collections.maxOfOrNull { it.projectNumber + 1 } ?: 1

    private fun <T> List<T>.insertAt(index: Int, value: T): List<T> =
        toMutableList().apply { add(index.coerceIn(0, size), value) }

    private fun newId(): String = UUID.randomUUID().toString()

    private fun String.isUniqueBarcode(): Boolean = count { it.isDigit() } > SIX_DIGITS

    private companion object {
        const val UNKNOWN_PRODUCT = "Ukjent vare"
        const val SIX_DIGITS = 6
    }
}
