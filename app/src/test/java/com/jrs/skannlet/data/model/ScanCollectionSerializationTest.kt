package com.jrs.skannlet.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanCollectionSerializationTest {
    @Test
    fun `legacy collection without creator still decodes`() {
        val collection = Json.decodeFromString<ScanCollection>(
            """
            {
                "id": "legacy-id",
                "projectNumber": 1,
                "name": "Legacy project",
                "createdAt": 100,
                "updatedAt": 200,
                "isLocked": false
            }
            """.trimIndent(),
        )

        assertNull(collection.creatorName)
        assertFalse(collection.isReturn)
    }

    @Test
    fun `return collection retains return marker when serialized`() {
        val original = ScanCollection(
            id = "return-id",
            projectNumber = 2,
            name = "Returprosjekt",
            createdAt = 100,
            updatedAt = 200,
            isReturn = true,
        )

        val decoded = Json.decodeFromString<ScanCollection>(Json.encodeToString(original))

        assertTrue(decoded.isReturn)
    }
}
