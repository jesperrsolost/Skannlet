package com.jrs.skannlet.data.repository

import com.jrs.skannlet.data.model.ScanCollection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionLockingTest {
    @Test
    fun `locking inactive collection preserves active collection`() {
        val result = lockCollectionState(
            collections = listOf(collection("active"), collection("target")),
            activeCollectionId = "active",
            collectionId = "target",
            updatedAt = 200L,
        )
        assertTrue(result is CollectionLockResult.Success)
        result as CollectionLockResult.Success

        assertEquals("active", result.activeCollectionId)
        assertTrue(result.collections.single { it.id == "target" }.isLocked)
        assertEquals(200L, result.collections.single { it.id == "target" }.updatedAt)
    }

    @Test
    fun `locking active collection selects first unlocked collection`() {
        val result = lockCollectionState(
            collections = listOf(collection("target"), collection("next")),
            activeCollectionId = "target",
            collectionId = "target",
            updatedAt = 200L,
        ) as CollectionLockResult.Success

        assertEquals("next", result.activeCollectionId)
    }

    @Test
    fun `locking last unlocked collection clears active collection`() {
        val result = lockCollectionState(
            collections = listOf(collection("target")),
            activeCollectionId = "target",
            collectionId = "target",
            updatedAt = 200L,
        ) as CollectionLockResult.Success

        assertNull(result.activeCollectionId)
    }

    @Test
    fun `missing collection is reported without changes`() {
        val result = lockCollectionState(
            collections = listOf(collection("active")),
            activeCollectionId = "active",
            collectionId = "missing",
            updatedAt = 200L,
        )

        assertSame(CollectionLockResult.Missing, result)
    }

    @Test
    fun `already locked collection is reported without changes`() {
        val result = lockCollectionState(
            collections = listOf(collection("target", isLocked = true)),
            activeCollectionId = null,
            collectionId = "target",
            updatedAt = 200L,
        )

        assertSame(CollectionLockResult.AlreadyLocked, result)
    }

    private fun collection(id: String, isLocked: Boolean = false) = ScanCollection(
        id = id,
        name = id,
        createdAt = 0L,
        updatedAt = 0L,
        isLocked = isLocked,
    )
}
