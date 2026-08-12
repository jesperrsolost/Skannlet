package com.jrs.skannlet

import com.jrs.skannlet.data.importer.ProductCsvImportException
import com.jrs.skannlet.data.importer.ProductCsvImporter
import java.nio.charset.Charset
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
    fun preservesNorwegianCharactersFromUtf8Csv() {
        val csv = "Produktnr.;Produkt\n123;Blå rørskål og æreskrans"

        val result = ProductCsvImporter.parse(csv.toByteArray(Charsets.UTF_8))

        assertEquals("Blå rørskål og æreskrans", result.products.single().productName)
    }

    @Test
    fun preservesNorwegianCharactersFromUtf8BomCsv() {
        val csv = "Produktnr.;Produkt\n123;Øyeskrue Ærlig Ås"
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            csv.toByteArray(Charsets.UTF_8)

        val result = ProductCsvImporter.parse(bytes)

        assertEquals("Øyeskrue Ærlig Ås", result.products.single().productName)
    }

    @Test
    fun preservesNorwegianCharactersFromWindows1252Csv() {
        val csv = "Produktnr.;Produkt\n123;Øyeskrue med blått rør og fjær"

        val result = ProductCsvImporter.parse(csv.toByteArray(Charset.forName("windows-1252")))

        assertEquals("Øyeskrue med blått rør og fjær", result.products.single().productName)
    }

    @Test
    fun throwsWhenRequiredColumnsAreMissing() {
        val csv = "Produktnr.;Navn\n123;Mangler produktkolonne"

        assertThrows(ProductCsvImportException::class.java) {
            ProductCsvImporter.parse(csv)
        }
    }
}
