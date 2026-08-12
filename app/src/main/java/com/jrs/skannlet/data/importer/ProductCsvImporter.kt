package com.jrs.skannlet.data.importer

import com.jrs.skannlet.data.model.Product
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Locale

data class ProductImportResult(
    val products: List<Product>,
    val skippedRows: Int,
)

class ProductCsvImportException(message: String) : Exception(message)

object ProductCsvImporter {
    fun parse(csvBytes: ByteArray): ProductImportResult = parse(csvBytes.decodeCsvText())

    fun parse(csvText: String): ProductImportResult {
        val normalizedText = csvText.removePrefix("\uFEFF")
        val delimiter = detectDelimiter(normalizedText)
        val records = parseRecords(normalizedText, delimiter)
            .filter { record -> record.any { it.isNotBlank() } }
        val header = records.firstOrNull()
            ?: throw ProductCsvImportException("Produktlisten er tom.")
        val barcodeIndex = header.indexOfHeader(BARCODE_HEADERS)
        val productIndex = header.indexOfHeader(PRODUCT_HEADERS)

        if (barcodeIndex == -1 || productIndex == -1) {
            throw ProductCsvImportException("Fant ikke kolonnene Produktnr. og Produkt.")
        }

        var skippedRows = 0
        val productsByBarcode = linkedMapOf<String, Product>()
        records.drop(1).forEach { record ->
            val barcode = record.getOrNull(barcodeIndex)?.trim().orEmpty()
            val productName = record.getOrNull(productIndex)?.trim().orEmpty()
            if (barcode.isBlank() || productName.isBlank()) {
                skippedRows++
            } else {
                productsByBarcode[barcode] = Product(
                    barcode = barcode,
                    productName = productName,
                )
            }
        }

        if (productsByBarcode.isEmpty()) {
            throw ProductCsvImportException("Produktlisten inneholder ingen gyldige produkter.")
        }

        return ProductImportResult(
            products = productsByBarcode.values.toList(),
            skippedRows = skippedRows,
        )
    }

    private fun detectDelimiter(text: String): Char {
        val firstLine = text.lineSequence().firstOrNull().orEmpty()
        return listOf(';', ',', '\t')
            .maxByOrNull { delimiter -> firstLine.countDelimiterOutsideQuotes(delimiter) }
            ?: ';'
    }

    private fun String.countDelimiterOutsideQuotes(delimiter: Char): Int {
        var inQuotes = false
        var count = 0
        var index = 0
        while (index < length) {
            val char = this[index]
            if (char == '"') {
                if (inQuotes && getOrNull(index + 1) == '"') {
                    index++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (char == delimiter && !inQuotes) {
                count++
            }
            index++
        }
        return count
    }

    private fun parseRecords(text: String, delimiter: Char): List<List<String>> {
        val records = mutableListOf<List<String>>()
        val record = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var index = 0

        fun finishField() {
            record += field.toString()
            field.clear()
        }

        fun finishRecord() {
            finishField()
            records += record.toList()
            record.clear()
        }

        while (index < text.length) {
            val char = text[index]
            when {
                char == '"' -> {
                    if (inQuotes && text.getOrNull(index + 1) == '"') {
                        field.append('"')
                        index++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                char == delimiter && !inQuotes -> finishField()
                (char == '\n' || char == '\r') && !inQuotes -> {
                    finishRecord()
                    if (char == '\r' && text.getOrNull(index + 1) == '\n') {
                        index++
                    }
                }
                else -> field.append(char)
            }
            index++
        }

        if (field.isNotEmpty() || record.isNotEmpty()) {
            finishRecord()
        }
        return records
    }

    private fun List<String>.indexOfHeader(acceptedHeaders: Set<String>): Int = indexOfFirst { header ->
        header.normalizeHeader() in acceptedHeaders
    }

    private fun String.normalizeHeader(): String = trim()
        .removePrefix("\uFEFF")
        .lowercase(Locale.ROOT)

    private val BARCODE_HEADERS = setOf("produktnr.", "produktnr")
    private val PRODUCT_HEADERS = setOf("produkt")
}

private fun ByteArray.decodeCsvText(): String {
    val utf8Bytes = if (startsWithUtf8Bom()) copyOfRange(UTF8_BOM_SIZE, size) else this
    return try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(utf8Bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        toString(WINDOWS_1252)
    }
}

private fun ByteArray.startsWithUtf8Bom(): Boolean =
    size >= UTF8_BOM_SIZE &&
        this[0] == UTF8_BOM[0] &&
        this[1] == UTF8_BOM[1] &&
        this[2] == UTF8_BOM[2]

private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
private const val UTF8_BOM_SIZE = 3
private val WINDOWS_1252 = java.nio.charset.Charset.forName("windows-1252")
