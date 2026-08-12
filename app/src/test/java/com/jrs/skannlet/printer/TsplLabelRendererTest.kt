package com.jrs.skannlet.printer

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TsplLabelRendererTest {
    @Test
    fun smallBarcodeRendersExactTc200TsplJob() {
        assertNull(validateLabelFormat(SmallBarcodeLabelFormat))

        val payload = TsplLabelRenderer.render(
            format = SmallBarcodeLabelFormat,
            data = LabelPrintData(barcode = "0100051826"),
        )

        assertEquals(
            """
            SIZE 35 mm,14 mm
            GAP 2.25 mm,0
            DIRECTION 1
            CLS
            BARCODE 16,8,"128",46,0,0,2,2,"0100051826"
            TEXT 36,62,"3",0,1,1,"01.0005.1826"
            PRINT 1
            """.trimIndent().replace("\n", "\r\n") + "\r\n",
            payload.toString(StandardCharsets.UTF_8),
        )
    }

    @Test
    fun preservesSignificantBarcodeSpaces() {
        val legacyFormat = SmallBarcodeLabelFormat.copy(
            id = "legacy",
            name = "Legacy",
            widthMm = 50.0,
            heightMm = 25.0,
            barcodeHeightMm = 14.0,
            contentLayout = LabelContentLayout.CenteredBarcodeValue,
        )
        val rendered = TsplLabelRenderer.render(
            format = legacyFormat,
            data = LabelPrintData(barcode = "  ABC-123  "),
        ).toString(StandardCharsets.UTF_8)

        assertTrue(rendered.contains("\"  ABC-123  \""))
    }

    @Test
    fun rejectsInvalidLabelGeometry() {
        val invalid = SmallBarcodeLabelFormat.copy(
            id = "too_narrow",
            name = "Too narrow",
            widthMm = 19.9,
        )

        assertEquals(
            "Etikettbredden må være mellom 20 og 112 mm for TC200.",
            validateLabelFormat(invalid),
        )
        val exception = assertThrows(InvalidLabelFormatException::class.java) {
            TsplLabelRenderer.render(invalid, LabelPrintData("123456"))
        }
        assertEquals(
            "Etikettbredden må være mellom 20 og 112 mm for TC200.",
            exception.message,
        )
    }

    @Test
    fun rejectsBlankUnsupportedAndOverflowingBarcodes() {
        val blank = assertThrows(InvalidBarcodeException::class.java) {
            TsplLabelRenderer.render(SmallBarcodeLabelFormat, LabelPrintData("   "))
        }
        assertEquals("Strekkoden er tom.", blank.message)

        val unsupported = assertThrows(InvalidBarcodeException::class.java) {
            TsplLabelRenderer.render(SmallBarcodeLabelFormat, LabelPrintData("123\nPRINT 99"))
        }
        assertEquals(
            "Strekkoden inneholder tegn som ikke støttes av etikettformatet.",
            unsupported.message,
        )

        val overflowing = assertThrows(InvalidBarcodeException::class.java) {
            TsplLabelRenderer.render(
                SmallBarcodeLabelFormat,
                LabelPrintData("ABCDEFGHIJKLMNOPQRSTUVWXYZ"),
            )
        }
        assertEquals("Strekkoden er for lang for valgt etikettformat.", overflowing.message)
    }

    @Test
    fun rendersConfiguredMediaTrackingCommand() {
        val blackMark = SmallBarcodeLabelFormat.copy(
            id = "black_mark",
            name = "Black mark",
            mediaTracking = LabelMediaTracking.BlackMark,
            trackingHeightMm = 2.0,
        )
        val continuous = blackMark.copy(
            id = "continuous",
            name = "Continuous",
            mediaTracking = LabelMediaTracking.Continuous,
        )

        val blackMarkJob = TsplLabelRenderer.render(blackMark, LabelPrintData("123456"))
            .toString(StandardCharsets.UTF_8)
        val continuousJob = TsplLabelRenderer.render(continuous, LabelPrintData("123456"))
            .toString(StandardCharsets.UTF_8)

        assertTrue(blackMarkJob.contains("\r\nBLINE 2 mm,0\r\n"))
        assertTrue(continuousJob.contains("\r\nGAP 0,0\r\n"))
    }

    @Test
    fun validatesTc200TrackingHeightRange() {
        assertEquals(
            "Mellomrom eller svartmerke må være mellom 2 og 25,4 mm for TC200.",
            validateLabelFormat(SmallBarcodeLabelFormat.copy(trackingHeightMm = 1.9)),
        )
        assertNull(validateLabelFormat(SmallBarcodeLabelFormat.copy(trackingHeightMm = 25.4)))
        assertNull(
            validateLabelFormat(
                SmallBarcodeLabelFormat.copy(
                    mediaTracking = LabelMediaTracking.Continuous,
                    trackingHeightMm = 0.0,
                ),
            ),
        )
    }

    @Test
    fun supportsSafePrintableCode128Punctuation() {
        val rendered = TsplLabelRenderer.render(
            SmallBarcodeLabelFormat,
            LabelPrintData("A+1:@"),
        ).toString(StandardCharsets.UTF_8)

        assertTrue(rendered.contains("\"A+1:@\""))
        assertTrue(rendered.contains("TEXT 36,62,\"3\",0,1,1,\"A+1:@\""))
    }

    @Test
    fun accountsForCodeSetSwitchForOddNumericBarcode() {
        val narrowFormat = SmallBarcodeLabelFormat.copy(
            id = "odd_numeric",
            name = "Odd numeric",
            widthMm = 48.0,
            moduleWidthDots = 4,
        )

        val exception = assertThrows(InvalidBarcodeException::class.java) {
            TsplLabelRenderer.render(narrowFormat, LabelPrintData("12345"))
        }

        assertEquals("Strekkoden er for lang for valgt etikettformat.", exception.message)
    }
}
