package com.jrs.skannlet.data.storage

import android.content.Context
import com.jrs.skannlet.data.model.AppUser
import com.jrs.skannlet.data.model.Product
import com.jrs.skannlet.data.model.ScanCollection
import com.jrs.skannlet.data.model.ScanRow
import com.jrs.skannlet.data.model.StoredAppState
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
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

class StorageReadException(
    val errors: List<String>,
) : IOException(
    "Sikkerhetskopien ble ikke opprettet fordi lokale data ikke kunne leses. " +
        errors.joinToString(" "),
)

class StorageRecoveryException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

internal enum class StorageTransactionOperation {
    STAGED_NEW,
    STAGED_OLD,
    PREPARED,
    INSTALLED_NEW,
    BEFORE_ROLLBACK,
    COMMITTED,
    JOURNAL_REMOVED,
}

internal fun interface StorageTransactionObserver {
    fun onOperation(operation: StorageTransactionOperation, fileName: String?)
}

private object NoOpStorageTransactionObserver : StorageTransactionObserver {
    override fun onOperation(operation: StorageTransactionOperation, fileName: String?) = Unit
}

class LocalJsonStorage internal constructor(
    private val filesDir: File,
    private val transactionObserver: StorageTransactionObserver = NoOpStorageTransactionObserver,
) {
    constructor(context: Context) : this(context.applicationContext.filesDir)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val usersFile = File(filesDir, USERS_FILE)
    private val collectionsFile = File(filesDir, COLLECTIONS_FILE)
    private val rowsFile = File(filesDir, ROWS_FILE)
    private val productsFile = File(filesDir, PRODUCTS_FILE)
    private val stateFile = File(filesDir, STATE_FILE)
    private val journalFile = File(filesDir, JOURNAL_FILE)
    private val journalTempFile = File(filesDir, JOURNAL_TEMP_FILE)
    private val transactionDir = File(filesDir, TRANSACTION_DIR)
    private val oldFilesDir = File(transactionDir, OLD_FILES_DIR)
    private val newFilesDir = File(transactionDir, NEW_FILES_DIR)

    suspend fun readAll(): StorageReadResult = withContext(Dispatchers.IO) {
        recoverPendingRestore()
        readAllFiles()
    }

    suspend fun readAllForBackup(): StoredData = withContext(Dispatchers.IO) {
        recoverPendingRestore()
        val read = readAllFiles()
        if (read.errors.isNotEmpty()) throw StorageReadException(read.errors)
        read.data
    }

    suspend fun saveUsers(users: List<AppUser>) = withContext(Dispatchers.IO) {
        recoverPendingRestore()
        writeJson(usersFile, users)
    }

    suspend fun saveCollections(collections: List<ScanCollection>) = withContext(Dispatchers.IO) {
        recoverPendingRestore()
        writeJson(collectionsFile, collections)
    }

    suspend fun saveRows(rows: List<ScanRow>) = withContext(Dispatchers.IO) {
        recoverPendingRestore()
        writeJson(rowsFile, rows)
    }

    suspend fun saveProducts(products: List<Product>) = withContext(Dispatchers.IO) {
        recoverPendingRestore()
        writeJson(productsFile, products)
    }

    suspend fun saveAppState(appState: StoredAppState) = withContext(Dispatchers.IO) {
        recoverPendingRestore()
        writeJson(stateFile, appState)
    }

    suspend fun replaceAll(data: StoredData) = withContext(Dispatchers.IO) {
        recoverPendingRestore()
        val newContents = encodeStoredData(data)
        val journal = prepareRestore(newContents)

        try {
            writeJournal(journal)
            transactionObserver.onOperation(StorageTransactionOperation.PREPARED, null)

            journal.files.forEach { entry ->
                installFile(
                    source = File(newFilesDir, entry.name),
                    target = dataFile(entry.name),
                    expectedSha256 = entry.newSha256,
                )
                transactionObserver.onOperation(StorageTransactionOperation.INSTALLED_NEW, entry.name)
            }
            verifyNewFiles(journal)

            writeJournal(journal.copy(phase = RestorePhase.COMMITTED))
            transactionObserver.onOperation(StorageTransactionOperation.COMMITTED, null)
            finishTransaction()
        } catch (exception: Exception) {
            val recoveredPhase = try {
                recoverPendingRestore()
            } catch (recoveryException: Exception) {
                throw StorageRecoveryException(
                    "Gjenopprettingen feilet, og lokale data kunne ikke tilbakeføres sikkert.",
                    recoveryException,
                ).also { it.addSuppressed(exception) }
            }

            if (recoveredPhase != RestorePhase.COMMITTED) throw exception
        }
    }

    private fun readAllFiles(): StorageReadResult {
        ensureProductsFile()
        val errors = mutableListOf<String>()
        return StorageReadResult(
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

    private fun encodeStoredData(data: StoredData): Map<String, ByteArray> = linkedMapOf(
        USERS_FILE to json.encodeToString(data.users).encodeToByteArray(),
        COLLECTIONS_FILE to json.encodeToString(data.collections).encodeToByteArray(),
        ROWS_FILE to json.encodeToString(data.rows).encodeToByteArray(),
        PRODUCTS_FILE to json.encodeToString(data.products).encodeToByteArray(),
        STATE_FILE to json.encodeToString(data.appState).encodeToByteArray(),
    )

    private fun prepareRestore(newContents: Map<String, ByteArray>): RestoreJournal {
        require(!journalFile.exists()) { "En tidligere gjenoppretting er ikke ferdigbehandlet." }
        deleteTransactionArtifacts(strict = true)
        require(oldFilesDir.mkdirs() && newFilesDir.mkdirs()) {
            "Kunne ikke opprette arbeidsområdet for gjenoppretting."
        }

        return try {
            val entries = DATA_FILE_NAMES.map { name ->
                val newFile = File(newFilesDir, name)
                writeSynced(newFile, newContents.getValue(name))
                val newSha256 = newFile.sha256()
                transactionObserver.onOperation(StorageTransactionOperation.STAGED_NEW, name)

                val currentFile = dataFile(name)
                val oldSha256 = if (currentFile.exists()) {
                    val oldFile = File(oldFilesDir, name)
                    copySynced(currentFile, oldFile)
                    transactionObserver.onOperation(StorageTransactionOperation.STAGED_OLD, name)
                    oldFile.sha256()
                } else {
                    null
                }
                RestoreFileEntry(
                    name = name,
                    oldExisted = oldSha256 != null,
                    oldSha256 = oldSha256,
                    newSha256 = newSha256,
                )
            }
            RestoreJournal(
                version = RESTORE_JOURNAL_VERSION,
                phase = RestorePhase.PREPARED,
                files = entries,
            )
        } catch (exception: Exception) {
            deleteTransactionArtifacts(strict = false)
            throw exception
        }
    }

    private fun recoverPendingRestore(): RestorePhase? {
        if (!journalFile.exists()) {
            deleteTransactionArtifacts(strict = false)
            return null
        }

        val journal = readJournal()
        return try {
            when (journal.phase) {
                RestorePhase.PREPARED -> rollbackPreparedRestore(journal)
                RestorePhase.COMMITTED -> rollForwardCommittedRestore(journal)
            }
            val recoveredPhase = journal.phase
            finishTransaction()
            recoveredPhase
        } catch (exception: StorageRecoveryException) {
            throw exception
        } catch (exception: Exception) {
            throw StorageRecoveryException(
                "En avbrutt gjenoppretting kunne ikke ferdigbehandles sikkert.",
                exception,
            )
        }
    }

    private fun rollbackPreparedRestore(journal: RestoreJournal) {
        journal.files.forEach { entry ->
            transactionObserver.onOperation(StorageTransactionOperation.BEFORE_ROLLBACK, entry.name)
            val target = dataFile(entry.name)
            if (entry.oldExisted) {
                val oldSha256 = requireNotNull(entry.oldSha256)
                if (!target.hasSha256(oldSha256)) {
                    installFile(
                        source = File(oldFilesDir, entry.name),
                        target = target,
                        expectedSha256 = oldSha256,
                    )
                }
            } else if (target.exists() && !target.delete()) {
                throw IOException("Kunne ikke fjerne ${entry.name} under tilbakeføring.")
            }
        }

        journal.files.forEach { entry ->
            val target = dataFile(entry.name)
            if (entry.oldExisted) {
                requireFileHash(target, requireNotNull(entry.oldSha256))
            } else if (target.exists()) {
                throw IOException("${entry.name} skulle ikke finnes etter tilbakeføring.")
            }
        }
    }

    private fun rollForwardCommittedRestore(journal: RestoreJournal) {
        journal.files.forEach { entry ->
            val target = dataFile(entry.name)
            if (!target.hasSha256(entry.newSha256)) {
                installFile(
                    source = File(newFilesDir, entry.name),
                    target = target,
                    expectedSha256 = entry.newSha256,
                )
            }
        }
        verifyNewFiles(journal)
    }

    private fun verifyNewFiles(journal: RestoreJournal) {
        journal.files.forEach { entry ->
            requireFileHash(dataFile(entry.name), entry.newSha256)
        }
    }

    private fun installFile(
        source: File,
        target: File,
        expectedSha256: String,
    ) {
        requireFileHash(source, expectedSha256)
        val installTemp = File(filesDir, ".${target.name}.restore.tmp")
        copySynced(source, installTemp)
        atomicReplace(installTemp, target)
        requireFileHash(target, expectedSha256)
    }

    private fun writeJournal(journal: RestoreJournal) {
        validateJournal(journal)
        writeSynced(journalTempFile, json.encodeToString(journal).encodeToByteArray())
        atomicReplace(journalTempFile, journalFile)
    }

    private fun readJournal(): RestoreJournal = try {
        json.decodeFromString<RestoreJournal>(journalFile.readText()).also(::validateJournal)
    } catch (exception: Exception) {
        throw StorageRecoveryException("Gjenopprettingsjournalen er ugyldig.", exception)
    }

    private fun validateJournal(journal: RestoreJournal) {
        require(journal.version == RESTORE_JOURNAL_VERSION) { "Ukjent journalversjon." }
        require(journal.files.map { it.name }.toSet() == DATA_FILE_NAMES.toSet()) {
            "Journalen inneholder feil filer."
        }
        require(journal.files.map { it.name }.distinct().size == journal.files.size) {
            "Journalen inneholder duplikate filer."
        }
        journal.files.forEach { entry ->
            require(entry.newSha256.matches(SHA256_PATTERN)) { "Ugyldig kontrollsum." }
            require(entry.oldExisted == (entry.oldSha256 != null)) { "Ugyldig tidligere filstatus." }
            require(entry.oldSha256 == null || entry.oldSha256.matches(SHA256_PATTERN)) {
                "Ugyldig tidligere kontrollsum."
            }
        }
    }

    private fun finishTransaction() {
        if (journalFile.exists() && !journalFile.delete()) {
            throw IOException("Kunne ikke ferdigstille gjenopprettingsjournalen.")
        }
        journalTempFile.delete()
        transactionObserver.onOperation(StorageTransactionOperation.JOURNAL_REMOVED, null)
        deleteTransactionArtifacts(strict = false)
    }

    private fun deleteTransactionArtifacts(strict: Boolean) {
        DATA_FILE_NAMES.forEach { name -> File(filesDir, ".$name.restore.tmp").delete() }
        journalTempFile.delete()
        if (!transactionDir.exists()) return
        val deleted = transactionDir.deleteRecursively()
        if (strict && !deleted) throw IOException("Kunne ikke rydde arbeidsområdet for gjenoppretting.")
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

    private fun writeSynced(file: File, bytes: ByteArray) {
        file.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs()) throw IOException("Kunne ikke opprette ${parent.name}.")
        }
        FileOutputStream(file).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
    }

    private fun copySynced(source: File, target: File) {
        target.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs()) throw IOException("Kunne ikke opprette ${parent.name}.")
        }
        FileInputStream(source).use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output)
                output.fd.sync()
            }
        }
    }

    private fun atomicReplace(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (exception: AtomicMoveNotSupportedException) {
            throw IOException("Filsystemet støtter ikke sikker atomisk gjenoppretting.", exception)
        }
    }

    private fun requireFileHash(file: File, expectedSha256: String) {
        if (!file.hasSha256(expectedSha256)) {
            throw IOException("Kontrollsummen for ${file.name} stemmer ikke.")
        }
    }

    private fun File.hasSha256(expectedSha256: String): Boolean =
        exists() && isFile && sha256() == expectedSha256

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(this).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun dataFile(name: String): File = when (name) {
        USERS_FILE -> usersFile
        COLLECTIONS_FILE -> collectionsFile
        ROWS_FILE -> rowsFile
        PRODUCTS_FILE -> productsFile
        STATE_FILE -> stateFile
        else -> throw IllegalArgumentException("Ukjent datafil: $name")
    }

    private companion object {
        const val USERS_FILE = "users.json"
        const val COLLECTIONS_FILE = "collections.json"
        const val ROWS_FILE = "scan_rows.json"
        const val PRODUCTS_FILE = "products.json"
        const val STATE_FILE = "app_state.json"
        const val JOURNAL_FILE = ".restore-journal.json"
        const val JOURNAL_TEMP_FILE = ".restore-journal.json.tmp"
        const val TRANSACTION_DIR = ".restore-txn"
        const val OLD_FILES_DIR = "old"
        const val NEW_FILES_DIR = "new"
        const val RESTORE_JOURNAL_VERSION = 1

        val DATA_FILE_NAMES = listOf(
            USERS_FILE,
            COLLECTIONS_FILE,
            ROWS_FILE,
            PRODUCTS_FILE,
            STATE_FILE,
        )
        val SHA256_PATTERN = Regex("[0-9a-f]{64}")
    }
}

@Serializable
private data class RestoreJournal(
    val version: Int,
    val phase: RestorePhase,
    val files: List<RestoreFileEntry>,
)

@Serializable
private data class RestoreFileEntry(
    val name: String,
    val oldExisted: Boolean,
    val oldSha256: String?,
    val newSha256: String,
)

@Serializable
private enum class RestorePhase {
    PREPARED,
    COMMITTED,
}
