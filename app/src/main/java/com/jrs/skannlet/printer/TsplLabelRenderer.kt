package com.jrs.skannlet.printer

import java.math.BigDecimal
import kotlin.math.min
import kotlin.math.roundToInt

private const val TC200_DOTS_PER_MM = 8
private const val TC200_MAX_MEDIA_WIDTH_MM = 112.0
private const val TC200_MAX_LABEL_LENGTH_MM = 2_286.0
private const val TC200_MAX_PRINT_WIDTH_DOTS = 864
private const val BARCODE_TOP_MARGIN_MM = 3.0
private const val BARCODE_VALUE_RESERVED_MM = 4.0
private const val COMPACT_BARCODE_X_DOTS = 16
private const val COMPACT_BARCODE_Y_DOTS = 8
private const val COMPACT_TEXT_X_DOTS = 36
private const val COMPACT_TEXT_Y_DOTS = 62
private const val COMPACT_TEXT_FONT_HEIGHT_DOTS = 24

internal object TsplLabelRenderer {
    fun render(format: LabelFormat, data: LabelPrintData): ByteArray {
        validateLabelFormat(format)?.let { throw InvalidLabelFormatException(it) }
        val barcode = data.barcode
        validateBarcode(barcode, format)

        val barcodeHeight = (format.barcodeHeightMm * TC200_DOTS_PER_MM).roundToInt()
        val moduleWidth = format.moduleWidthDots
        val mediaTrackingCommand = format.mediaTrackingCommand(
            zeroOffsetWithUnit = format.contentLayout == LabelContentLayout.CenteredBarcodeValue,
        )
        val commands = when (format.contentLayout) {
            LabelContentLayout.CenteredBarcodeValue -> {
                val centerX = (format.widthMm * TC200_DOTS_PER_MM / 2.0).roundToInt()
                val barcodeY = (BARCODE_TOP_MARGIN_MM * TC200_DOTS_PER_MM).roundToInt()
                listOf(
                    "SIZE ${format.widthMm.tsplNumber()} mm,${format.heightMm.tsplNumber()} mm",
                    mediaTrackingCommand,
                    "DIRECTION 1,0",
                    "REFERENCE 0,0",
                    "CODEPAGE UTF-8",
                    "CLS",
                    "BARCODE $centerX,$barcodeY,\"128\",$barcodeHeight,2,0,$moduleWidth,$moduleWidth,2,\"$barcode\"",
                    "PRINT 1,1",
                )
            }

            LabelContentLayout.CompactBarcodeWithText -> listOf(
                "SIZE ${format.widthMm.tsplNumber()} mm,${format.heightMm.tsplNumber()} mm",
                mediaTrackingCommand,
                "DIRECTION 1",
                "CLS",
                "BARCODE $COMPACT_BARCODE_X_DOTS,$COMPACT_BARCODE_Y_DOTS,\"128\",$barcodeHeight,0,0,$moduleWidth,$moduleWidth,\"$barcode\"",
                "TEXT $COMPACT_TEXT_X_DOTS,$COMPACT_TEXT_Y_DOTS,\"3\",0,1,1,\"${barcode.displayValue()}\"",
                "PRINT 1",
            )
        }
        return (commands.joinToString("\r\n") + "\r\n").toByteArray(Charsets.UTF_8)
    }
}

private fun LabelFormat.mediaTrackingCommand(zeroOffsetWithUnit: Boolean): String {
    val zeroOffset = if (zeroOffsetWithUnit) "0 mm" else "0"
    return when (mediaTracking) {
        LabelMediaTracking.Gap -> "GAP ${trackingHeightMm.tsplNumber()} mm,$zeroOffset"
        LabelMediaTracking.BlackMark -> "BLINE ${trackingHeightMm.tsplNumber()} mm,$zeroOffset"
        LabelMediaTracking.Continuous -> "GAP 0,0"
    }
}

fun validateLabelFormat(format: LabelFormat): String? = when {
    format.name.isBlank() -> "Formatet må ha et navn."
    format.widthMm !in 20.0..TC200_MAX_MEDIA_WIDTH_MM ->
        "Etikettbredden må være mellom 20 og 112 mm for TC200."
    format.heightMm !in 10.0..TC200_MAX_LABEL_LENGTH_MM ->
        "Etiketthøyden må være mellom 10 og 2286 mm for TC200."
    format.mediaTracking != LabelMediaTracking.Continuous && format.trackingHeightMm !in 2.0..25.4 ->
        "Mellomrom eller svartmerke må være mellom 2 og 25,4 mm for TC200."
    format.contentLayout == LabelContentLayout.CompactBarcodeWithText &&
        format.heightMm * TC200_DOTS_PER_MM < COMPACT_TEXT_Y_DOTS + COMPACT_TEXT_FONT_HEIGHT_DOTS ->
        "Etiketthøyden er for liten for tekstlinjen."
    format.barcodeHeightMm !in 5.0..format.maxBarcodeHeightMm() ->
        "Strekkoden er for høy for etiketten."
    format.moduleWidthDots !in 1..4 -> "Modulbredden må være mellom 1 og 4 punkter."
    else -> null
}

private fun validateBarcode(barcode: String, format: LabelFormat) {
    if (barcode.isBlank()) throw InvalidBarcodeException("Strekkoden er tom.")
    if (!barcode.all(::isSafePrintableCode128Character)) {
        throw InvalidBarcodeException("Strekkoden inneholder tegn som ikke støttes av etikettformatet.")
    }

    val dataSymbols = if (barcode.all(Char::isDigit)) {
        when {
            barcode.length == 1 -> 1
            barcode.length % 2 == 0 -> barcode.length / 2
            else -> barcode.length / 2 + 2
        }
    } else {
        barcode.length
    }
    val estimatedWidthDots = (11 * (dataSymbols + 2) + 13 + 20) * format.moduleWidthDots
    val labelWidthDots = (format.widthMm * TC200_DOTS_PER_MM).roundToInt()
    val centerX = labelWidthDots / 2
    val printableHalfWidth = min(centerX - 16, TC200_MAX_PRINT_WIDTH_DOTS - centerX - 16)
    val availableWidthDots = printableHalfWidth * 2
    if (estimatedWidthDots > availableWidthDots) {
        throw InvalidBarcodeException("Strekkoden er for lang for valgt etikettformat.")
    }
}

private fun isSafePrintableCode128Character(character: Char): Boolean =
    character.code in 0x20..0x7E && character != '"' && character != '\\'

private fun LabelFormat.maxBarcodeHeightMm(): Double = when (contentLayout) {
    LabelContentLayout.CenteredBarcodeValue -> heightMm - BARCODE_TOP_MARGIN_MM - BARCODE_VALUE_RESERVED_MM
    LabelContentLayout.CompactBarcodeWithText ->
        (COMPACT_TEXT_Y_DOTS - COMPACT_BARCODE_Y_DOTS).toDouble() / TC200_DOTS_PER_MM
}

private fun String.displayValue(): String = if (length == 10 && all(Char::isDigit)) {
    "${substring(0, 2)}.${substring(2, 6)}.${substring(6, 10)}"
} else {
    this
}

private fun Double.tsplNumber(): String = BigDecimal.valueOf(this).stripTrailingZeros().toPlainString()

internal class InvalidLabelFormatException(message: String) : IllegalArgumentException(message)
internal class InvalidBarcodeException(message: String) : IllegalArgumentException(message)
