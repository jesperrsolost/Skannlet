package com.jrs.skannlet.data.storage

import com.jrs.skannlet.data.model.AppUser
import com.jrs.skannlet.data.model.Product
import com.jrs.skannlet.data.model.ScanCollection
import com.jrs.skannlet.data.model.ScanRow
import com.jrs.skannlet.data.model.StoredAppState
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalJsonStorageTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `successful restore replaces all files and removes transaction artifacts`() = runBlocking {
        val root = temporaryFolder.newFolder("success")
        val storage = LocalJsonStorage(root)
        storage.replaceAll(oldData())

        storage.replaceAll(newData())

        assertEquals(newData(), storage.readAll().data)
        assertTransactionArtifactsRemoved(root)
    }

    @Test
    fun `crashes before commit always recover the complete old data set`() {
        val crashPoints = buildList {
            add(StorageTransactionOperation.STAGED_NEW to "users.json")
            add(StorageTransactionOperation.STAGED_OLD to "users.json")
            add(StorageTransactionOperation.PREPARED to null)
            DATA_FILE_NAMES.forEach { name ->
                add(StorageTransactionOperation.INSTALLED_NEW to name)
            }
        }

        crashPoints.forEachIndexed { index, (operation, fileName) ->
            val root = temporaryFolder.newFolder("pre-commit-$index")
            runBlocking { LocalJsonStorage(root).replaceAll(oldData()) }
            val crashingStorage = LocalJsonStorage(
                filesDir = root,
                transactionObserver = CrashObserver(operation, fileName),
            )

            assertThrows(SimulatedProcessDeath::class.java) {
                runBlocking { crashingStorage.replaceAll(newData()) }
            }

            val recovered = runBlocking { LocalJsonStorage(root).readAll() }
            assertEquals("Failure at $operation/$fileName", oldData(), recovered.data)
            assertTransactionArtifactsRemoved(root)
        }
    }

    @Test
    fun `crash after commit rolls forward to the complete new data set`() {
        val root = temporaryFolder.newFolder("committed")
        runBlocking { LocalJsonStorage(root).replaceAll(oldData()) }
        val crashingStorage = LocalJsonStorage(
            filesDir = root,
            transactionObserver = CrashObserver(StorageTransactionOperation.COMMITTED, null),
        )

        assertThrows(SimulatedProcessDeath::class.java) {
            runBlocking { crashingStorage.replaceAll(newData()) }
        }

        assertEquals(newData(), runBlocking { LocalJsonStorage(root).readAll() }.data)
        assertTransactionArtifactsRemoved(root)
    }

    @Test
    fun `committed recovery repairs a missing live file from staging`() {
        val root = temporaryFolder.newFolder("committed-repair")
        runBlocking { LocalJsonStorage(root).replaceAll(oldData()) }
        val crashingStorage = LocalJsonStorage(
            filesDir = root,
            transactionObserver = CrashObserver(StorageTransactionOperation.COMMITTED, null),
        )
        assertThrows(SimulatedProcessDeath::class.java) {
            runBlocking { crashingStorage.replaceAll(newData()) }
        }
        assertTrue(File(root, "users.json").delete())

        assertEquals(newData(), runBlocking { LocalJsonStorage(root).readAll() }.data)
        assertTransactionArtifactsRemoved(root)
    }

    @Test
    fun `crash after journal removal keeps new data and cleans orphan staging`() {
        val root = temporaryFolder.newFolder("journal-removed")
        runBlocking { LocalJsonStorage(root).replaceAll(oldData()) }
        val crashingStorage = LocalJsonStorage(
            filesDir = root,
            transactionObserver = CrashObserver(StorageTransactionOperation.JOURNAL_REMOVED, null),
        )

        assertThrows(SimulatedProcessDeath::class.java) {
            runBlocking { crashingStorage.replaceAll(newData()) }
        }

        assertEquals(newData(), runBlocking { LocalJsonStorage(root).readAll() }.data)
        assertTransactionArtifactsRemoved(root)
    }

    @Test
    fun `ordinary failure after every file replacement rolls back before returning`() {
        DATA_FILE_NAMES.forEachIndexed { index, fileName ->
            val root = temporaryFolder.newFolder("immediate-rollback-$index")
            runBlocking { LocalJsonStorage(root).replaceAll(oldData()) }
            val observer = OneShotFailureObserver(
                operation = StorageTransactionOperation.INSTALLED_NEW,
                fileName = fileName,
            )

            assertThrows(IOException::class.java) {
                runBlocking {
                    LocalJsonStorage(root, observer).replaceAll(newData())
                }
            }

            assertEquals(oldData(), runBlocking { LocalJsonStorage(root).readAll() }.data)
            assertTransactionArtifactsRemoved(root)
        }
    }

    @Test
    fun `cancellation before commit rolls back before propagating`() = runBlocking {
        val root = temporaryFolder.newFolder("cancelled-restore")
        LocalJsonStorage(root).replaceAll(oldData())
        val observer = OneShotCancellationObserver()

        assertThrows(CancellationException::class.java) {
            runBlocking { LocalJsonStorage(root, observer).replaceAll(newData()) }
        }

        assertEquals(oldData(), LocalJsonStorage(root).readAll().data)
        assertTransactionArtifactsRemoved(root)
    }

    @Test
    fun `failed rollback stays journaled and a later access retries before reading`() = runBlocking {
        val root = temporaryFolder.newFolder("retry-rollback")
        LocalJsonStorage(root).replaceAll(oldData())
        val observer = CommitAndRollbackFailureObserver()
        val storage = LocalJsonStorage(root, observer)

        assertThrows(StorageRecoveryException::class.java) {
            runBlocking { storage.replaceAll(newData()) }
        }
        assertTrue(File(root, ".restore-journal.json").exists())

        observer.failRollback = false
        assertEquals(oldData(), storage.readAll().data)
        assertTransactionArtifactsRemoved(root)
    }

    @Test
    fun `rollback restores malformed original bytes instead of decoded defaults`() = runBlocking {
        val root = temporaryFolder.newFolder("raw-rollback")
        LocalJsonStorage(root).replaceAll(oldData())
        val malformedRows = "{malformed rows"
        File(root, "scan_rows.json").writeText(malformedRows)
        val crashingStorage = LocalJsonStorage(
            filesDir = root,
            transactionObserver = CrashObserver(
                StorageTransactionOperation.INSTALLED_NEW,
                "scan_rows.json",
            ),
        )

        assertThrows(SimulatedProcessDeath::class.java) {
            runBlocking { crashingStorage.replaceAll(newData()) }
        }

        val recovered = LocalJsonStorage(root).readAll()
        assertTrue(recovered.errors.isNotEmpty())
        assertEquals(malformedRows, File(root, "scan_rows.json").readText())
    }

    @Test
    fun `strict backup read rejects every unreadable data file`() {
        DATA_FILE_NAMES.forEachIndexed { index, fileName ->
            val root = temporaryFolder.newFolder("strict-read-$index")
            runBlocking { LocalJsonStorage(root).replaceAll(oldData()) }
            File(root, fileName).writeText("{not valid json")

            val exception = assertThrows(StorageReadException::class.java) {
                runBlocking { LocalJsonStorage(root).readAllForBackup() }
            }

            assertTrue(exception.errors.isNotEmpty())
            assertTrue(exception.message.orEmpty().contains("Sikkerhetskopien ble ikke opprettet"))
        }
    }

    private fun assertTransactionArtifactsRemoved(root: File) {
        assertFalse(File(root, ".restore-journal.json").exists())
        assertFalse(File(root, ".restore-txn").exists())
        assertFalse(root.listFiles().orEmpty().any { it.name.endsWith(".restore.tmp") })
    }

    private fun oldData(): StoredData = storedData("old", 1L)

    private fun newData(): StoredData = storedData("new", 100L)

    private fun storedData(prefix: String, baseTime: Long): StoredData {
        val user = AppUser(id = "$prefix-user", name = "$prefix user", createdAt = baseTime)
        val collection = ScanCollection(
            id = "$prefix-collection",
            projectNumber = baseTime.toInt(),
            name = "$prefix project",
            createdAt = baseTime + 1,
            updatedAt = baseTime + 2,
        )
        return StoredData(
            users = listOf(user),
            collections = listOf(collection),
            rows = listOf(
                ScanRow(
                    id = "$prefix-row",
                    collectionId = collection.id,
                    barcode = "$prefix-barcode",
                    productName = "$prefix product",
                    quantity = 1.5f,
                    quantityLocked = false,
                    createdAt = baseTime + 3,
                    comment = "$prefix comment",
                ),
            ),
            products = listOf(Product("$prefix-barcode", "$prefix product")),
            appState = StoredAppState(
                activeUserId = user.id,
                activeCollectionId = collection.id,
                nextCollectionProjectNumber = baseTime.toInt() + 1,
            ),
        )
    }

    private class CrashObserver(
        private val operation: StorageTransactionOperation,
        private val fileName: String?,
    ) : StorageTransactionObserver {
        override fun onOperation(operation: StorageTransactionOperation, fileName: String?) {
            if (operation == this.operation && fileName == this.fileName) {
                throw SimulatedProcessDeath()
            }
        }
    }

    private class OneShotFailureObserver(
        private val operation: StorageTransactionOperation,
        private val fileName: String?,
    ) : StorageTransactionObserver {
        private var failed = false

        override fun onOperation(operation: StorageTransactionOperation, fileName: String?) {
            if (!failed && operation == this.operation && fileName == this.fileName) {
                failed = true
                throw IOException("Injected storage failure")
            }
        }
    }

    private class CommitAndRollbackFailureObserver : StorageTransactionObserver {
        var failRollback = true
        private var commitFailed = false

        override fun onOperation(operation: StorageTransactionOperation, fileName: String?) {
            if (!commitFailed && operation == StorageTransactionOperation.INSTALLED_NEW && fileName == "scan_rows.json") {
                commitFailed = true
                throw IOException("Injected commit failure")
            }
            if (failRollback && operation == StorageTransactionOperation.BEFORE_ROLLBACK && fileName == "users.json") {
                throw IOException("Injected rollback failure")
            }
        }
    }

    private class OneShotCancellationObserver : StorageTransactionObserver {
        private var cancelled = false

        override fun onOperation(operation: StorageTransactionOperation, fileName: String?) {
            if (!cancelled && operation == StorageTransactionOperation.INSTALLED_NEW && fileName == "scan_rows.json") {
                cancelled = true
                throw CancellationException("Injected cancellation")
            }
        }
    }

    private class SimulatedProcessDeath : Error()

    private companion object {
        val DATA_FILE_NAMES = listOf(
            "users.json",
            "collections.json",
            "scan_rows.json",
            "products.json",
            "app_state.json",
        )
    }
}
