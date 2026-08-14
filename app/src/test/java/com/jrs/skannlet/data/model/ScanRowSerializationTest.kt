package com.jrs.skannlet.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanRowSerializationTest {
    @Test
    fun `legacy integer quantity decodes as float`() {
        val row = Json.decodeFromString<ScanRow>(
            """
            {
                "id": "row-id",
                "collectionId": "collection-id",
                "barcode": "123456",
                "productName": "Testprodukt",
                "quantity": 2,
                "quantityLocked": false,
                "createdAt": 100
            }
            """.trimIndent(),
        )

        assertEquals(2f, row.quantity, 0f)
        assertEquals("", row.comment)
    }

    @Test
    fun `fractional quantity survives serialization round trip`() {
        val original = ScanRow(
            id = "row-id",
            collectionId = "collection-id",
            barcode = "123456",
            productName = "Testprodukt",
            quantity = 1.5f,
            quantityLocked = false,
            createdAt = 100,
            comment = "Kontroller emballasjen",
        )

        val decoded = Json.decodeFromString<ScanRow>(Json.encodeToString(original))

        assertEquals(1.5f, decoded.quantity, 0f)
        assertEquals("Kontroller emballasjen", decoded.comment)
    }
}
