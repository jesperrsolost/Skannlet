package com.jrs.skannlet.printer

import kotlinx.serialization.Serializable

const val SMALL_BARCODE_FORMAT_ID = "small_barcode"
const val DEFAULT_PRINTER_PORT = 9100

@Serializable
data class LabelFormat(
    val id: String,
    val name: String,
    val widthMm: Double,
    val heightMm: Double,
    val trackingHeightMm: Double,
    val barcodeHeightMm: Double,
    val moduleWidthDots: Int,
    val mediaTracking: LabelMediaTracking = LabelMediaTracking.Gap,
    val contentLayout: LabelContentLayout = LabelContentLayout.CenteredBarcodeValue,
)

@Serializable
enum class LabelMediaTracking {
    Gap,
    BlackMark,
    Continuous,
}

@Serializable
enum class LabelContentLayout {
    CenteredBarcodeValue,
    CompactBarcodeWithText,
}

val SmallBarcodeLabelFormat = LabelFormat(
    id = SMALL_BARCODE_FORMAT_ID,
    name = "Small Barcode",
    widthMm = 35.0,
    heightMm = 14.0,
    trackingHeightMm = 2.25,
    barcodeHeightMm = 5.75,
    moduleWidthDots = 2,
    contentLayout = LabelContentLayout.CompactBarcodeWithText,
)

val LabelFormat.isBuiltIn: Boolean
    get() = id == SMALL_BARCODE_FORMAT_ID

data class LabelPrinterSettings(
    val ipAddress: String = "",
    val port: Int = DEFAULT_PRINTER_PORT,
    val selectedFormatId: String = SMALL_BARCODE_FORMAT_ID,
    val customFormats: List<LabelFormat> = emptyList(),
) {
    val formats: List<LabelFormat>
        get() = listOf(SmallBarcodeLabelFormat) + customFormats.filterNot(LabelFormat::isBuiltIn)

    val selectedFormat: LabelFormat
        get() = formats.firstOrNull { it.id == selectedFormatId } ?: SmallBarcodeLabelFormat
}

data class PrinterEndpoint(
    val address: String,
    val port: Int,
)

data class LabelPrintData(
    val barcode: String,
)

enum class TscPrinterStatus(
    val code: Int,
    val description: String,
) {
    Ready(0x00, "klar"),
    HeadOpen(0x01, "skrivehodet er åpent"),
    PaperJam(0x02, "papirstopp"),
    PaperJamAndHeadOpen(0x03, "papirstopp, skrivehodet er åpent"),
    OutOfPaper(0x04, "tom for etiketter"),
    OutOfPaperAndHeadOpen(0x05, "tom for etiketter, skrivehodet er åpent"),
    OutOfRibbon(0x08, "tom for fargebånd"),
    OutOfRibbonAndHeadOpen(0x09, "tom for fargebånd, skrivehodet er åpent"),
    OutOfRibbonAndPaperJam(0x0A, "tom for fargebånd, papirstopp"),
    OutOfRibbonPaperJamAndHeadOpen(0x0B, "tom for fargebånd, papirstopp, skrivehodet er åpent"),
    OutOfRibbonAndPaper(0x0C, "tom for fargebånd, tom for etiketter"),
    OutOfRibbonPaperAndHeadOpen(0x0D, "tom for fargebånd, tom for etiketter, skrivehodet er åpent"),
    Paused(0x10, "satt på pause"),
    Printing(0x20, "skriver"),
    OtherError(0x80, "annen feil"),
    ;

    companion object {
        fun fromCode(code: Int): TscPrinterStatus = entries.firstOrNull { it.code == code }
            ?: throw UnexpectedTscStatusException(code)
    }
}

data class TscConnectionTestResult(
    val status: TscPrinterStatus,
    val model: String?,
)
