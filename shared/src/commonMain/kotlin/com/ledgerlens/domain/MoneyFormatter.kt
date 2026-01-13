package com.ledgerlens.domain

import kotlin.math.abs

/**
 * Formatter for converting Money to canonical string representation.
 * No locale/currency symbol formatting - that belongs in platform UI.
 */
object MoneyFormatter {
    /**
     * Canonical major-units string (e.g., "12.34") for export/debugging.
     * Locale/currency symbol formatting belongs in platform UI.
     */
    fun toMajorString(money: Money): String {
        val sign = if (money.minorUnits < 0) "-" else ""
        val absValue = abs(money.minorUnits)

        val scale = money.scale
        if (scale == 0) return sign + absValue.toString()

        val divisor = (1L..scale).fold(1L) { acc, _ -> acc * 10L }
        val whole = absValue / divisor
        val frac = absValue % divisor
        return sign + whole.toString() + "." + frac.toString().padStart(scale, '0')
    }
}
