package com.jrs.skannlet.ui.profile.printer

import com.jrs.skannlet.printer.LabelFormat
import com.jrs.skannlet.printer.LabelMediaTracking

internal fun buildEditedLabelFormat(
    initialFormat: LabelFormat,
    name: String,
    width: String,
    height: String,
    trackingHeight: String,
    barcodeHeight: String,
    moduleWidth: String,
    mediaTracking: LabelMediaTracking,
): LabelFormat = initialFormat.copy(
    name = name,
    widthMm = width.toDecimalOrNaN(),
    heightMm = height.toDecimalOrNaN(),
    trackingHeightMm = if (mediaTracking == LabelMediaTracking.Continuous) {
        0.0
    } else {
        trackingHeight.toDecimalOrNaN()
    },
    barcodeHeightMm = barcodeHeight.toDecimalOrNaN(),
    moduleWidthDots = moduleWidth.toIntOrNull() ?: 0,
    mediaTracking = mediaTracking,
)

internal fun Double.toEditableNumber(): String = toString().removeSuffix(".0")

internal fun String.toDecimalOrNaN(): Double = replace(',', '.').toDoubleOrNull() ?: Double.NaN

internal fun String.toDecimalInput(): String = filterIndexed { index, character ->
    character.isDigit() || (character == ',' || character == '.') && index > 0 &&
        take(index).none { it == ',' || it == '.' }
}.take(7)

internal fun nextFormatName(base: String, formats: List<LabelFormat>): String {
    if (formats.none { it.name.equals(base, ignoreCase = true) }) return base

    var suffix = 2
    while (formats.any { it.name.equals("$base $suffix", ignoreCase = true) }) {
        suffix++
    }
    return "$base $suffix"
}

internal fun String.isValidIpv4Address(): Boolean {
    val parts = split('.')
    return parts.size == 4 && parts.all { part ->
        val value = part.toIntOrNull()
        part.isNotEmpty() && part.length <= 3 && part.all(Char::isDigit) &&
            value != null && value in 0..255
    }
}
