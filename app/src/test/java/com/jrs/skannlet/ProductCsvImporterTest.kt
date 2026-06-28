package com.jrs.skannlet

import com.jrs.skannlet.data.importer.ProductCsvImportException
import com.jrs.skannlet.data.importer.ProductCsvImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProductCsvImporterTest {
    @Test
    fun parsesProductNumberAndProductColumnsFromSemicolonCsv() {
        val csv = """
            Gruppe;Produktnr.;Produkt;Lager
            Elektrisk;11063006;HS 63A/230V Skap;Kongsvinger
            Elektrisk;11063026;HS 63A/230V Skap;Kongsvinger
        """.trimIndent()

        val result = ProductCsvImporter.parse(csv)

        assertEquals(2, result.products.size)
        assertEquals("11063006", result.products[0].barcode)
        assertEquals("HS 63A/230V Skap", result.products[0].productName)
        assertEquals(0, result.skippedRows)
    }

    @Test
    fun throwsWhenRequiredColumnsAreMissing() {
        val csv = "Produktnr.;Navn\n123;Mangler produktkolonne"

        assertThrows(ProductCsvImportException::class.java) {
            ProductCsvImporter.parse(csv)
        }
    }
}
