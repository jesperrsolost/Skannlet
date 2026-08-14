package com.jrs.skannlet.data.repository

import com.jrs.skannlet.data.model.ScanCollection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionReturnStatusTest {
    @Test
    fun `marks unlocked collection as return and updates timestamp`() {
        val result = updateCollectionReturnStatus(
            collections = listOf(collection("target"), collection("other")),
            collectionId = "target",
            isReturn = true,
            updatedAt = 200L,
        ) as CollectionReturnStatusResult.Success

        val updated = result.collections.single { it.id == "target" }
        assertTrue(updated.isReturn)
        assertEquals(200L, updated.updatedAt)
        assertFalse(result.collections.single { it.id == "other" }.isReturn)
    }

    @Test
    fun `removes return marking`() {
        val result = updateCollectionReturnStatus(
            collections = listOf(collection("target", isReturn = true)),
            collectionId = "target",
            isReturn = false,
            updatedAt = 200L,
        ) as CollectionReturnStatusResult.Success

        assertFalse(result.collections.single().isReturn)
    }

    @Test
    fun `locked collection is not changed`() {
        val result = updateCollectionReturnStatus(
            collections = listOf(collection("target", isLocked = true)),
            collectionId = "target",
            isReturn = true,
            updatedAt = 200L,
        )

        assertSame(CollectionReturnStatusResult.Locked, result)
    }

    @Test
    fun `missing collection is reported`() {
        val result = updateCollectionReturnStatus(
            collections = emptyList(),
            collectionId = "missing",
            isReturn = true,
            updatedAt = 200L,
        )

        assertSame(CollectionReturnStatusResult.Missing, result)
    }

    @Test
    fun `matching return status is unchanged`() {
        val result = updateCollectionReturnStatus(
            collections = listOf(collection("target", isReturn = true)),
            collectionId = "target",
            isReturn = true,
            updatedAt = 200L,
        )

        assertSame(CollectionReturnStatusResult.Unchanged, result)
    }

    private fun collection(
        id: String,
        isReturn: Boolean = false,
        isLocked: Boolean = false,
    ) = ScanCollection(
        id = id,
        name = id,
        createdAt = 0L,
        updatedAt = 100L,
        isLocked = isLocked,
        isReturn = isReturn,
    )
}
