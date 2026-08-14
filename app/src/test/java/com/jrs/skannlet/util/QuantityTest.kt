package com.jrs.skannlet.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuantityTest {
    @Test
    fun `parses comma and dot quantities`() {
        assertEquals(1.5f, parseQuantity("1,5")!!, 0f)
        assertEquals(0.125f, parseQuantity("0.125")!!, 0f)
    }

    @Test
    fun `rejects invalid quantities`() {
        listOf("", "0", "-1", "1,2345", "1,2,3", "NaN", "Infinity").forEach { value ->
            assertNull(value, parseQuantity(value))
        }
    }

    @Test
    fun `formats quantities with Norwegian decimal comma`() {
        assertEquals("1", formatQuantity(1f))
        assertEquals("1,5", formatQuantity(1.5f))
        assertEquals("1,25", formatQuantity(1.25f))
    }

    @Test
    fun `buttons increment and decrement by one`() {
        assertEquals(2.5f, incrementQuantity(1.5f)!!, 0f)
        assertEquals(0.5f, decrementQuantity(1.5f)!!, 0f)
        assertNull(decrementQuantity(0.5f))
        assertNull(decrementQuantity(1f))
    }

    @Test
    fun `normalizes quantities to three decimals`() {
        assertEquals(1.235f, 1.2346f.normalizedQuantityOrNull()!!, 0f)
    }
}
