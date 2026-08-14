package com.jrs.skannlet.ui.collections

import com.jrs.skannlet.app.CollectionDetailUiState
import com.jrs.skannlet.app.ScanRowUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionPrintDocumentTest {
    @Test
    fun `print document uses stored collection creator`() {
        val document = collectionDetail(creatorName = "Kari Nordmann").toPrintDocument()

        assertTrue(document.metaText.contains("Opprettet av: Kari Nordmann"))
        assertFalse(document.metaText.contains("printet av"))
    }

    @Test
    fun `print document uses unknown user for legacy collection`() {
        val document = collectionDetail(creatorName = null).toPrintDocument()

        assertTrue(document.metaText.contains("Opprettet av: Ukjent bruker"))
    }

    @Test
    fun `print document uses delivery title for ordinary collection`() {
        val document = collectionDetail(creatorName = "Kari Nordmann").toPrintDocument()

        assertEquals("Utlevering #12 | Testprosjekt", document.title)
    }

    @Test
    fun `print document uses return title for return collection`() {
        val document = collectionDetail(
            creatorName = "Kari Nordmann",
            isReturn = true,
        ).toPrintDocument()

        assertEquals("Retur #12 | Testprosjekt", document.title)
    }

    @Test
    fun `delivery PDF and print job names start with delivery document type`() {
        val detail = collectionDetail(creatorName = "Kari Nordmann")

        assertEquals("Utlevering_Testprosjekt.pdf", detail.printFileName())
        assertEquals("Utlevering_Testprosjekt", detail.printJobName())
    }

    @Test
    fun `return PDF and print job names start with return document type`() {
        val detail = collectionDetail(
            creatorName = "Kari Nordmann",
            isReturn = true,
        )

        assertEquals("Retur_Testprosjekt.pdf", detail.printFileName())
        assertEquals("Retur_Testprosjekt", detail.printJobName())
    }

    @Test
    fun `print document formats fractional quantity with decimal comma`() {
        val row = ScanRowUiState(
            id = "row-id",
            barcode = "123456",
            productName = "Testprodukt",
            quantity = 1.5f,
            quantityLocked = false,
            createdAt = 0L,
        )

        val document = collectionDetail(
            creatorName = "Kari Nordmann",
            rows = listOf(row),
        ).toPrintDocument()

        assertEquals("1,5", document.rows.single().quantity)
    }

    @Test
    fun `print document includes row comment`() {
        val row = ScanRowUiState(
            id = "row-id",
            barcode = "123456",
            productName = "Testprodukt",
            quantity = 1f,
            quantityLocked = false,
            createdAt = 0L,
            comment = "Leveres til andre etasje",
        )

        val document = collectionDetail(
            creatorName = "Kari Nordmann",
            rows = listOf(row),
        ).toPrintDocument()

        assertEquals("Leveres til andre etasje", document.rows.single().comment)
    }

    private fun collectionDetail(
        creatorName: String?,
        isReturn: Boolean = false,
        rows: List<ScanRowUiState> = emptyList(),
    ) = CollectionDetailUiState(
        id = "collection-id",
        projectNumber = 12,
        name = "Testprosjekt",
        creatorName = creatorName,
        scanCount = 0,
        updatedAt = 0L,
        isActive = true,
        isLocked = false,
        isReturn = isReturn,
        rows = rows,
    )
}
