package com.jrs.skannlet.data.backup

import com.jrs.skannlet.data.model.AppUser
import com.jrs.skannlet.data.model.Product
import com.jrs.skannlet.data.model.ScanCollection
import com.jrs.skannlet.data.model.ScanRow
import com.jrs.skannlet.data.model.StoredAppState
import com.jrs.skannlet.data.storage.StoredData
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AppDataBackupTest {
    @Test
    fun `backup survives round trip`() {
        val original = storedData()

        val restored = parseAppDataBackup(
            buildAppDataBackup(original, appVersion = "1.1.2", createdAt = 100L),
        )

        assertEquals(original, restored)
    }

    @Test
    fun `backup rejects unknown zip entries`() {
        val bytes = ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("../users.json"))
                zip.write("[]".encodeToByteArray())
                zip.closeEntry()
            }
            output.toByteArray()
        }

        assertThrows(IllegalArgumentException::class.java) {
            parseAppDataBackup(bytes)
        }
    }

    @Test
    fun `backup rejects incomplete archive`() {
        val bytes = ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("users.json"))
                zip.write("[]".encodeToByteArray())
                zip.closeEntry()
            }
            output.toByteArray()
        }

        assertThrows(IllegalArgumentException::class.java) {
            parseAppDataBackup(bytes)
        }
    }

    @Test
    fun `backup rejects changed file contents`() {
        val original = buildAppDataBackup(storedData(), appVersion = "1.1.2", createdAt = 100L)
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(original.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
            }
        }
        entries["users.json"] = entries.getValue("users.json") + " ".encodeToByteArray()
        val changed = ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { (name, content) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content)
                    zip.closeEntry()
                }
            }
            output.toByteArray()
        }

        assertThrows(IllegalArgumentException::class.java) {
            parseAppDataBackup(changed)
        }
    }

    private fun storedData(): StoredData {
        val user = AppUser(id = "user", name = "Kari", createdAt = 1L)
        val collection = ScanCollection(
            id = "collection",
            name = "Prosjekt",
            createdAt = 2L,
            updatedAt = 3L,
        )
        return StoredData(
            users = listOf(user),
            collections = listOf(collection),
            rows = listOf(
                ScanRow(
                    id = "row",
                    collectionId = collection.id,
                    barcode = "123456",
                    productName = "Testprodukt",
                    quantityLocked = false,
                    createdAt = 4L,
                    comment = "Kommentar",
                ),
            ),
            products = listOf(Product(barcode = "123456", productName = "Testprodukt")),
            appState = StoredAppState(
                activeUserId = user.id,
                activeCollectionId = collection.id,
                nextCollectionProjectNumber = 2,
            ),
        )
    }
}
