package com.jrs.skannlet.ui.collections

import com.jrs.skannlet.app.CollectionDetailUiState
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

    private fun collectionDetail(
        creatorName: String?,
        isReturn: Boolean = false,
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
        rows = emptyList(),
    )
}
