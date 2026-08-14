package com.jrs.skannlet.data.export

import com.jrs.skannlet.data.model.ScanRow
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvExporterTest {
    @Test
    fun `delivery CSV filename starts with delivery document type`() {
        assertEquals(
            "Utlevering_Kari_Nordmann_Testprosjekt_2rader_20260813_120000.csv",
            buildCollectionCsvFileName(
                userName = "Kari Nordmann",
                collectionName = "Testprosjekt",
                isReturn = false,
                rowCount = 2,
                timestamp = "20260813_120000",
            ),
        )
    }

    @Test
    fun `return CSV filename starts with return document type`() {
        assertEquals(
            "Retur_Kari_Nordmann_Testprosjekt_2rader_20260813_120000.csv",
            buildCollectionCsvFileName(
                userName = "Kari Nordmann",
                collectionName = "Testprosjekt",
                isReturn = true,
                rowCount = 2,
                timestamp = "20260813_120000",
            ),
        )
    }

    @Test
    fun `exports fractional quantity with decimal comma`() {
        val row = ScanRow(
            id = "row-id",
            collectionId = "collection-id",
            barcode = "123456",
            productName = "Testprodukt",
            quantity = 1.5f,
            quantityLocked = false,
            createdAt = 100,
        )

        assertEquals(
            "antall;strekkode\n1,5;123456\n",
            buildCollectionCsv(listOf(row)),
        )
    }
}
