package com.ledgerlens.money

import kotlin.math.absoluteValue

/**
 * Represents monetary amounts using integer minor units (e.g., cents for USD).
 * This avoids floating-point precision issues inherent in financial calculations.
 *
 * @property minorUnits The amount in minor units (e.g., cents). 1234 = $12.34 for USD.
 * @property currencyCode ISO 4217 currency code (e.g., "USD", "EUR").
 */
data class Money(
    val minorUnits: Long,
    val currencyCode: String = "USD"
) : Comparable<Money> {

    val isZero: Boolean get() = minorUnits == 0L
    val isPositive: Boolean get() = minorUnits > 0
    val isNegative: Boolean get() = minorUnits < 0

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return copy(minorUnits = minorUnits + other.minorUnits)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return copy(minorUnits = minorUnits - other.minorUnits)
    }

    operator fun times(multiplier: Int): Money =
        copy(minorUnits = minorUnits * multiplier)

    operator fun times(multiplier: Long): Money =
        copy(minorUnits = minorUnits * multiplier)

    operator fun unaryMinus(): Money =
        copy(minorUnits = -minorUnits)

    fun abs(): Money =
        copy(minorUnits = minorUnits.absoluteValue)

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return minorUnits.compareTo(other.minorUnits)
    }

    private fun requireSameCurrency(other: Money) {
        require(currencyCode == other.currencyCode) {
            "Cannot operate on different currencies: $currencyCode vs ${other.currencyCode}"
        }
    }

    /**
     * Formats the money as a string with decimal point.
     * Uses the currency's standard decimal places (2 for most currencies).
     */
    fun toDecimalString(): String {
        val scale = CurrencyMetadata.getScale(currencyCode)
        val divisor = powOf10(scale)
        val sign = if (minorUnits < 0) "-" else ""
        val absolute = minorUnits.absoluteValue
        val wholePart = absolute / divisor
        val fractionalPart = absolute % divisor
        return "$sign$wholePart.${fractionalPart.toString().padStart(scale, '0')}"
    }

    /**
     * Formats with currency symbol.
     */
    fun toFormattedString(): String {
        val symbol = CurrencyMetadata.getSymbol(currencyCode)
        return if (isNegative) {
            "-$symbol${(-this).toDecimalString().removePrefix("-")}"
        } else {
            "$symbol${toDecimalString()}"
        }
    }

    override fun toString(): String = toFormattedString()

    companion object {
        val ZERO = Money(0L, "USD")

        fun fromMajorUnits(majorUnits: Long, currencyCode: String = "USD"): Money {
            val scale = CurrencyMetadata.getScale(currencyCode)
            return Money(majorUnits * powOf10(scale), currencyCode)
        }

        fun fromDecimal(wholeUnits: Long, fractionalUnits: Int, currencyCode: String = "USD"): Money {
            val scale = CurrencyMetadata.getScale(currencyCode)
            val divisor = powOf10(scale)
            val minor = wholeUnits * divisor + fractionalUnits
            return Money(minor, currencyCode)
        }

        private fun powOf10(exp: Int): Long {
            var result = 1L
            repeat(exp) { result *= 10 }
            return result
        }
    }
}
