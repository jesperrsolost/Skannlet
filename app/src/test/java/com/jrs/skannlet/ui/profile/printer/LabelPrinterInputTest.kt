package com.jrs.skannlet.ui.profile.printer

import com.jrs.skannlet.printer.LabelContentLayout
import com.jrs.skannlet.printer.LabelMediaTracking
import com.jrs.skannlet.printer.SmallBarcodeLabelFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelPrinterInputTest {
    @Test
    fun editingFormatPreservesIdentityAndContentLayout() {
        val initialFormat = SmallBarcodeLabelFormat.copy(
            id = "custom",
            name = "Tilpasset",
        )
        val edited = buildEditedLabelFormat(
            initialFormat = initialFormat,
            name = "Oppdatert",
            width = "40,5",
            height = "20",
            trackingHeight = "2.5",
            barcodeHeight = "10",
            moduleWidth = "3",
            mediaTracking = LabelMediaTracking.Gap,
        )

        assertEquals("custom", edited.id)
        assertEquals(LabelContentLayout.CompactBarcodeWithText, edited.contentLayout)
        assertEquals("Oppdatert", edited.name)
        assertEquals(40.5, edited.widthMm, 0.0)
        assertEquals(3, edited.moduleWidthDots)
    }

    @Test
    fun continuousMediaClearsUnusedTrackingHeight() {
        val edited = buildEditedLabelFormat(
            initialFormat = SmallBarcodeLabelFormat,
            name = "Kontinuerlig",
            width = "40",
            height = "20",
            trackingHeight = "not a number",
            barcodeHeight = "10",
            moduleWidth = "2",
            mediaTracking = LabelMediaTracking.Continuous,
        )

        assertEquals(0.0, edited.trackingHeightMm, 0.0)
    }

    @Test
    fun validatesIpv4OctetsAndStructure() {
        assertTrue("10.0.0.1".isValidIpv4Address())
        assertTrue("192.168.1.255".isValidIpv4Address())
        assertFalse("192.168.1".isValidIpv4Address())
        assertFalse("192.168.1.256".isValidIpv4Address())
        assertFalse("192.168..1".isValidIpv4Address())
    }

    @Test
    fun choosesNextFormatNameWithoutCaseSensitiveCollisions() {
        val formats = listOf(
            SmallBarcodeLabelFormat.copy(id = "1", name = "EGET FORMAT"),
            SmallBarcodeLabelFormat.copy(id = "2", name = "Eget format 2"),
        )

        assertEquals("Eget format 3", nextFormatName("Eget format", formats))
        assertEquals("Lager", nextFormatName("Lager", formats))
    }

    @Test
    fun decimalInputKeepsOneSeparatorWithinLengthLimit() {
        assertEquals("12,34", "12,3.4x".toDecimalInput())
        assertEquals("1234567", "123456789".toDecimalInput())
        assertEquals("5.25", "5.25".toDecimalInput())
    }
}
