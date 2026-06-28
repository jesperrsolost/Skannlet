package com.jrs.skannlet.data.storage

import android.content.Context
import com.jrs.skannlet.data.model.AppUser
import com.jrs.skannlet.data.model.Product
import com.jrs.skannlet.data.model.ScanCollection
import com.jrs.skannlet.data.model.ScanRow
import com.jrs.skannlet.data.model.StoredAppState
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class StoredData(
    val users: List<AppUser>,
    val collections: List<ScanCollection>,
    val rows: List<ScanRow>,
    val products: List<Product>,
    val appState: StoredAppState,
)

data class StorageReadResult(
    val data: StoredData,
    val errors: List<String>,
)

class LocalJsonStorage(context: Context) {
    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val usersFile = File(appContext.filesDir, USERS_FILE)
    private val collectionsFile = File(appContext.filesDir, COLLECTIONS_FILE)
    private val rowsFile = File(appContext.filesDir, ROWS_FILE)
    private val productsFile = File(appContext.filesDir, PRODUCTS_FILE)
    private val stateFile = File(appContext.filesDir, STATE_FILE)

    suspend fun readAll(): StorageReadResult = withContext(Dispatchers.IO) {
        ensureProductsFile()
        val errors = mutableListOf<String>()
        StorageReadResult(
            data = StoredData(
                users = readJson(usersFile, emptyList(), "Brukere", errors),
                collections = readJson(collectionsFile, emptyList(), "Samlinger", errors),
                rows = readJson(rowsFile, emptyList(), "Skannede rader", errors),
                products = readJson(productsFile, emptyList(), "Produkter", errors),
                appState = readJson(stateFile, StoredAppState(), "Aktiv appstatus", errors),
            ),
            errors = errors,
        )
    }

    suspend fun saveUsers(users: List<AppUser>) = withContext(Dispatchers.IO) {
        writeJson(usersFile, users)
    }

    suspend fun saveCollections(collections: List<ScanCollection>) = withContext(Dispatchers.IO) {
        writeJson(collectionsFile, collections)
    }

    suspend fun saveRows(rows: List<ScanRow>) = withContext(Dispatchers.IO) {
        writeJson(rowsFile, rows)
    }

    suspend fun saveProducts(products: List<Product>) = withContext(Dispatchers.IO) {
        writeJson(productsFile, products)
    }

    suspend fun saveAppState(appState: StoredAppState) = withContext(Dispatchers.IO) {
        writeJson(stateFile, appState)
    }

    private fun ensureProductsFile() {
        if (!productsFile.exists()) {
            writeJson(
                productsFile,
                listOf(
                    Product(barcode = "123456", productName = "Demo vare - seks siffer"),
                    Product(barcode = "7038010000017", productName = "Demo vare - strekkode"),
                ),
            )
        }
    }

    private inline fun <reified T> readJson(
        file: File,
        defaultValue: T,
        label: String,
        errors: MutableList<String>,
    ): T {
        if (!file.exists()) return defaultValue
        return try {
            json.decodeFromString(file.readText())
        } catch (_: SerializationException) {
            errors += "$label kunne ikke leses. Filen ignoreres."
            defaultValue
        } catch (_: IllegalArgumentException) {
            errors += "$label kunne ikke leses. Filen ignoreres."
            defaultValue
        } catch (_: IOException) {
            errors += "$label kunne ikke leses. Filen ignoreres."
            defaultValue
        }
    }

    private inline fun <reified T> writeJson(file: File, value: T) {
        file.parentFile?.mkdirs()
        val encodedValue = json.encodeToString(value)
        val tempFile = File(file.parentFile, "${file.name}.tmp")
        tempFile.writeText(encodedValue)
        if (!tempFile.renameTo(file)) {
            file.writeText(encodedValue)
            tempFile.delete()
        }
    }

    private companion object {
        const val USERS_FILE = "users.json"
        const val COLLECTIONS_FILE = "collections.json"
        const val ROWS_FILE = "scan_rows.json"
        const val PRODUCTS_FILE = "products.json"
        const val STATE_FILE = "app_state.json"
    }
}
