package com.jrs.skannlet.printer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelPrinterSettingsJsonCodecTest {
    @Test
    fun migratesLegacyGapFieldWithoutLosingEndpointOrSelection() {
        val legacyJson = """
            {
              "ipAddress": "192.168.10.42",
              "port": 9101,
              "selectedFormatId": "legacy_custom",
              "customFormats": [
                {
                  "id": "legacy_custom",
                  "name": "Lageretikett",
                  "widthMm": 60.0,
                  "heightMm": 30.0,
                  "gapMm": 2.5,
                  "barcodeHeightMm": 16.0,
                  "moduleWidthDots": 2
                }
              ]
            }
        """.trimIndent()

        val settings = LabelPrinterSettingsJsonCodec.decode(legacyJson)

        assertEquals("192.168.10.42", settings.ipAddress)
        assertEquals(9101, settings.port)
        assertEquals("legacy_custom", settings.selectedFormatId)
        assertEquals(2.5, settings.selectedFormat.trackingHeightMm, 0.0)
        assertEquals(LabelMediaTracking.Gap, settings.selectedFormat.mediaTracking)
        assertEquals(LabelContentLayout.CenteredBarcodeValue, settings.selectedFormat.contentLayout)

        val reencoded = LabelPrinterSettingsJsonCodec.encode(settings)
        assertTrue(reencoded.contains("\"trackingHeightMm\":2.5"))
        assertFalse(reencoded.contains("\"gapMm\""))
    }
}
