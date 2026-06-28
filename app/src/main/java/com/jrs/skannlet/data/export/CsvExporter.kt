package com.jrs.skannlet.data.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.jrs.skannlet.data.model.AppUser
import com.jrs.skannlet.data.model.ScanCollection
import com.jrs.skannlet.data.model.ScanRow
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ExportedCsv(
    val uri: Uri,
    val fileName: String,
)

class CsvExporter(context: Context) {
    private val appContext = context.applicationContext

    fun exportCollection(
        user: AppUser,
        collection: ScanCollection,
        rows: List<ScanRow>,
    ): ExportedCsv {
        val exportDir = File(appContext.cacheDir, "exports").apply { mkdirs() }
        val fileName = buildFileName(user.name, collection.name, rows.size)
        val file = File(exportDir, fileName)

        file.writeText(buildCsv(rows))

        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file,
        )
        return ExportedCsv(uri = uri, fileName = fileName)
    }

    private fun buildCsv(rows: List<ScanRow>): String = buildString {
        appendLine("antall;strekkode")
        rows.forEach { row ->
            appendCsvValue(row.quantity.toString())
            append(';')
            appendCsvValue(row.barcode)
            append('\n')
        }
    }

    private fun StringBuilder.appendCsvValue(value: String) {
        append(value.replace("\"", "\"\""))
    }

    private fun buildFileName(userName: String, collectionName: String, rowCount: Int): String {
        val timestamp = TimestampFormatter.format(Instant.now())
        return "${sanitizeFilePart(userName)}_${sanitizeFilePart(collectionName)}_${rowCount}rader_$timestamp.csv"
    }

    private fun sanitizeFilePart(value: String): String {
        val sanitized = value.trim()
            .replace(Regex("\\s+"), "_")
            .replace(Regex("[^\\p{L}\\p{N}._-]"), "")
        return sanitized.ifBlank { "navn" }
    }

    private companion object {
        val TimestampFormatter: DateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyyMMdd_HHmmss")
            .withZone(ZoneId.systemDefault())
    }
}
