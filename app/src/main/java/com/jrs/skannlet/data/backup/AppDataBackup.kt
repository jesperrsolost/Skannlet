package com.jrs.skannlet.data.backup

import com.jrs.skannlet.data.model.AppUser
import com.jrs.skannlet.data.model.Product
import com.jrs.skannlet.data.model.ScanCollection
import com.jrs.skannlet.data.model.ScanRow
import com.jrs.skannlet.data.model.StoredAppState
import com.jrs.skannlet.data.storage.StoredData
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val BACKUP_FORMAT_VERSION = 1
private const val MAX_BACKUP_BYTES = 25 * 1024 * 1024

private val BackupJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}

private val DataFileNames = listOf(
    "users.json",
    "collections.json",
    "scan_rows.json",
    "products.json",
    "app_state.json",
)

@Serializable
private data class BackupManifest(
    val formatVersion: Int,
    val appVersion: String,
    val createdAt: Long,
    val files: List<BackupFile>,
)

@Serializable
private data class BackupFile(
    val name: String,
    val size: Int,
    val sha256: String,
)

internal fun buildAppDataBackup(
    data: StoredData,
    appVersion: String,
    createdAt: Long,
): ByteArray {
    val files = linkedMapOf(
        "users.json" to BackupJson.encodeToString(data.users).encodeToByteArray(),
        "collections.json" to BackupJson.encodeToString(data.collections).encodeToByteArray(),
        "scan_rows.json" to BackupJson.encodeToString(data.rows).encodeToByteArray(),
        "products.json" to BackupJson.encodeToString(data.products).encodeToByteArray(),
        "app_state.json" to BackupJson.encodeToString(data.appState).encodeToByteArray(),
    )
    require(files.values.sumOf { it.size.toLong() } <= MAX_BACKUP_BYTES) {
        "Appdataene er for store til én sikkerhetskopi."
    }
    val manifest = BackupManifest(
        formatVersion = BACKUP_FORMAT_VERSION,
        appVersion = appVersion,
        createdAt = createdAt,
        files = files.map { (name, bytes) ->
            BackupFile(name = name, size = bytes.size, sha256 = bytes.sha256())
        },
    )

    return ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(BackupJson.encodeToString(manifest).encodeToByteArray())
            zip.closeEntry()
            files.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        output.toByteArray()
    }
}

internal fun parseAppDataBackup(bytes: ByteArray): StoredData {
    require(bytes.size <= MAX_BACKUP_BYTES) { "Sikkerhetskopien er for stor." }
    val entries = mutableMapOf<String, ByteArray>()
    var totalUncompressedBytes = 0
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            require(!entry.isDirectory && entry.name == entry.name.substringAfterLast('/')) {
                "Sikkerhetskopien inneholder en ugyldig filsti."
            }
            require(entry.name == "manifest.json" || entry.name in DataFileNames) {
                "Sikkerhetskopien inneholder ukjente filer."
            }
            require(entry.name !in entries) { "Sikkerhetskopien inneholder duplikate filer." }
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val count = zip.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
                require(output.size() <= MAX_BACKUP_BYTES) { "Sikkerhetskopien er for stor." }
            }
            entries[entry.name] = output.toByteArray()
            totalUncompressedBytes += output.size()
            require(totalUncompressedBytes <= MAX_BACKUP_BYTES) { "Sikkerhetskopien er for stor." }
            zip.closeEntry()
        }
    }

    val manifestBytes = requireNotNull(entries["manifest.json"]) { "Manifest mangler." }
    val manifest = BackupJson.decodeFromString<BackupManifest>(manifestBytes.decodeToString())
    require(manifest.formatVersion == BACKUP_FORMAT_VERSION) { "Sikkerhetskopiformatet støttes ikke." }
    require(manifest.files.map { it.name }.distinct().size == manifest.files.size) {
        "Manifestet inneholder duplikate filer."
    }
    require(manifest.files.map { it.name }.toSet() == DataFileNames.toSet()) { "Sikkerhetskopien er ufullstendig." }
    require(entries.keys == (DataFileNames + "manifest.json").toSet()) { "Sikkerhetskopien er ufullstendig." }
    manifest.files.forEach { file ->
        val content = requireNotNull(entries[file.name]) { "Filen ${file.name} mangler." }
        require(content.size == file.size && content.sha256() == file.sha256) {
            "Kontrollsummen for ${file.name} stemmer ikke."
        }
    }

    return StoredData(
        users = BackupJson.decodeFromString<List<AppUser>>(entries.getValue("users.json").decodeToString()),
        collections = BackupJson.decodeFromString<List<ScanCollection>>(entries.getValue("collections.json").decodeToString()),
        rows = BackupJson.decodeFromString<List<ScanRow>>(entries.getValue("scan_rows.json").decodeToString()),
        products = BackupJson.decodeFromString<List<Product>>(entries.getValue("products.json").decodeToString()),
        appState = BackupJson.decodeFromString<StoredAppState>(entries.getValue("app_state.json").decodeToString()),
    ).also(::validateBackupReferences)
}

private fun validateBackupReferences(data: StoredData) {
    require(data.users.map { it.id }.distinct().size == data.users.size) { "Duplikate bruker-ID-er." }
    require(data.collections.map { it.id }.distinct().size == data.collections.size) { "Duplikate prosjekt-ID-er." }
    require(data.rows.map { it.id }.distinct().size == data.rows.size) { "Duplikate rad-ID-er." }
    val collectionIds = data.collections.mapTo(mutableSetOf()) { it.id }
    require(data.rows.all { it.collectionId in collectionIds }) { "En varerad peker på et ukjent prosjekt." }
    require(data.appState.activeUserId == null || data.users.any { it.id == data.appState.activeUserId }) {
        "Aktiv bruker finnes ikke."
    }
    require(data.appState.activeCollectionId == null || data.appState.activeCollectionId in collectionIds) {
        "Aktivt prosjekt finnes ikke."
    }
}

private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString("") { byte -> "%02x".format(byte) }
