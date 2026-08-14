package com.jrs.skannlet.data.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.jrs.skannlet.data.model.AppUser
import com.jrs.skannlet.data.model.ScanCollection
import com.jrs.skannlet.data.model.ScanRow
import com.jrs.skannlet.util.formatQuantity
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
        val fileName = buildCollectionCsvFileName(
            userName = user.name,
            collectionName = collection.name,
            isReturn = collection.isReturn,
            rowCount = rows.size,
            timestamp = TimestampFormatter.format(Instant.now()),
        )
        val file = File(exportDir, fileName)

        file.writeText(buildCollectionCsv(rows))

        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file,
        )
        return ExportedCsv(uri = uri, fileName = fileName)
    }

    private companion object {
        val TimestampFormatter: DateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyyMMdd_HHmmss")
            .withZone(ZoneId.systemDefault())
    }
}

internal fun buildCollectionCsvFileName(
    userName: String,
    collectionName: String,
    isReturn: Boolean,
    rowCount: Int,
    timestamp: String,
): String = buildString {
    append(collectionDocumentType(isReturn))
    append('_')
    append(sanitizeFilePart(userName))
    append('_')
    append(sanitizeFilePart(collectionName))
    append('_')
    append(rowCount)
    append("rader_")
    append(timestamp)
    append(".csv")
}

private fun sanitizeFilePart(value: String): String {
    val sanitized = value.trim()
        .replace(Regex("\\s+"), "_")
        .replace(Regex("[^\\p{L}\\p{N}._-]"), "")
    return sanitized.ifBlank { "navn" }
}

internal fun buildCollectionCsv(rows: List<ScanRow>): String = buildString {
    appendLine("antall;strekkode")
    rows.forEach { row ->
        appendCsvValue(formatQuantity(row.quantity))
        append(';')
        appendCsvValue(row.barcode)
        append('\n')
    }
}

private fun StringBuilder.appendCsvValue(value: String) {
    append(value.replace("\"", "\"\""))
}
