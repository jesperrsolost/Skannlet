package com.jrs.skannlet.data.repository

import com.jrs.skannlet.data.model.ScanCollection
import com.jrs.skannlet.data.model.ScanRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ScanRowCommentUpdateTest {
    @Test
    fun `adds trimmed comment and updates collection timestamp`() {
        val result = updateScanRowCommentState(
            rows = listOf(row()),
            collections = listOf(collection()),
            rowId = "row-id",
            comment = "  Leveres\n til andre etasje  ",
            updatedAt = 200L,
        ) as ScanRowCommentUpdateResult.Success

        assertEquals("Leveres til andre etasje", result.rows.single().comment)
        assertEquals(200L, result.collections.single().updatedAt)
    }

    @Test
    fun `clears existing comment`() {
        val result = updateScanRowCommentState(
            rows = listOf(row(comment = "Gammel kommentar")),
            collections = listOf(collection()),
            rowId = "row-id",
            comment = "   ",
            updatedAt = 200L,
        ) as ScanRowCommentUpdateResult.Success

        assertEquals("", result.rows.single().comment)
    }

    @Test
    fun `rejects comments longer than maximum`() {
        val result = updateScanRowCommentState(
            rows = listOf(row()),
            collections = listOf(collection()),
            rowId = "row-id",
            comment = "a".repeat(201),
            updatedAt = 200L,
        )

        assertSame(ScanRowCommentUpdateResult.TooLong, result)
    }

    @Test
    fun `rejects changes to locked collection`() {
        val result = updateScanRowCommentState(
            rows = listOf(row()),
            collections = listOf(collection(isLocked = true)),
            rowId = "row-id",
            comment = "Kommentar",
            updatedAt = 200L,
        )

        assertSame(ScanRowCommentUpdateResult.Locked, result)
    }

    @Test
    fun `reports missing row`() {
        val result = updateScanRowCommentState(
            rows = emptyList(),
            collections = listOf(collection()),
            rowId = "missing",
            comment = "Kommentar",
            updatedAt = 200L,
        )

        assertSame(ScanRowCommentUpdateResult.Missing, result)
    }

    @Test
    fun `reports unchanged normalized comment`() {
        val result = updateScanRowCommentState(
            rows = listOf(row(comment = "Samme kommentar")),
            collections = listOf(collection()),
            rowId = "row-id",
            comment = " Samme  kommentar ",
            updatedAt = 200L,
        )

        assertSame(ScanRowCommentUpdateResult.Unchanged, result)
    }

    private fun row(comment: String = "") = ScanRow(
        id = "row-id",
        collectionId = "collection-id",
        barcode = "123456",
        productName = "Testprodukt",
        quantityLocked = false,
        createdAt = 100L,
        comment = comment,
    )

    private fun collection(isLocked: Boolean = false) = ScanCollection(
        id = "collection-id",
        name = "Testprosjekt",
        createdAt = 100L,
        updatedAt = 100L,
        isLocked = isLocked,
    )
}
