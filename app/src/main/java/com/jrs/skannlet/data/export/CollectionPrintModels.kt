package com.jrs.skannlet.data.export

data class CollectionPrintDocument(
    val title: String,
    val metaText: String,
    val rows: List<CollectionPrintRow>,
)

data class CollectionPrintRow(
    val quantity: String,
    val barcode: String,
    val productName: String,
    val createdAt: String,
    val comment: String = "",
)

internal fun collectionDocumentType(isReturn: Boolean): String =
    if (isReturn) "Retur" else "Utlevering"
