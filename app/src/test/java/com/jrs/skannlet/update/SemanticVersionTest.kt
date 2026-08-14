package com.jrs.skannlet.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticVersionTest {
    @Test
    fun `parses plain and tagged stable versions`() {
        assertEquals(SemanticVersion(1, 2, 3), SemanticVersion.parse("1.2.3"))
        assertEquals(SemanticVersion(1, 2, 3), SemanticVersion.parse("v1.2.3"))
    }

    @Test
    fun `orders each semantic component numerically`() {
        assertTrue(SemanticVersion(2, 0, 0) > SemanticVersion(1, 99, 99))
        assertTrue(SemanticVersion(1, 10, 0) > SemanticVersion(1, 9, 99))
        assertTrue(SemanticVersion(1, 1, 10) > SemanticVersion(1, 1, 9))
    }

    @Test
    fun `rejects prerelease and malformed tags`() {
        assertNull(SemanticVersion.parse("v1.2.3-beta"))
        assertNull(SemanticVersion.parse("1.2"))
        assertNull(SemanticVersion.parse("release-1.2.3"))
    }
}
