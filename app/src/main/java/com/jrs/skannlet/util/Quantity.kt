package com.jrs.skannlet.util

import java.math.BigDecimal
import java.math.RoundingMode

private const val QuantityScale = 3
private val QuantityInputPattern = Regex("^\\d+(?:[,.]\\d{1,$QuantityScale})?$")

fun parseQuantity(value: String): Float? {
    val normalizedInput = value.trim()
    if (!QuantityInputPattern.matches(normalizedInput)) return null

    return normalizedInput
        .replace(',', '.')
        .toFloatOrNull()
        ?.normalizedQuantityOrNull()
}

fun Float.normalizedQuantityOrNull(): Float? {
    if (!isFinite() || this <= 0f) return null
    val normalized = BigDecimal(toString())
        .setScale(QuantityScale, RoundingMode.HALF_UP)
        .toFloat()
    return normalized.takeIf { it.isFinite() && it > 0f }
}

fun incrementQuantity(quantity: Float): Float? =
    (quantity + 1f).normalizedQuantityOrNull()?.takeIf { it > quantity }

fun decrementQuantity(quantity: Float): Float? =
    (quantity - 1f).normalizedQuantityOrNull()

fun formatQuantity(quantity: Float): String {
    if (!quantity.isFinite()) return quantity.toString()
    return BigDecimal(quantity.toString())
        .setScale(QuantityScale, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
        .replace('.', ',')
}
