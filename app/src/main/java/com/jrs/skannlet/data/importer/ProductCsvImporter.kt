package com.jrs.skannlet.data.importer

import com.jrs.skannlet.data.model.Product
import java.util.Locale

data class ProductImportResult(
    val products: List<Product>,
    val skippedRows: Int,
)

class ProductCsvImportException(message: String) : Exception(message)

object ProductCsvImporter {
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
