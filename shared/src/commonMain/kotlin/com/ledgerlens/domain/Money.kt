package com.ledgerlens.domain

import kotlin.math.absoluteValue

/**
 * Represents a monetary amount with precise decimal arithmetic.
 *
 * All amounts are stored as integer minor units (e.g., cents for USD).
 * This eliminates floating-point precision issues.
 *
 * @property minorUnits The amount in the smallest currency unit
 * @property currencyCode ISO 4217 currency code
 * @property scale Number of decimal places for this currency
 */
data class Money(
    val minorUnits: Long,
    val currencyCode: String,
    val scale: Int = CurrencyMetadata.getScale(currencyCode)
) : Comparable<Money> {

    init {
        require(currencyCode.length == 3) { "Currency code must be 3 characters" }
        require(scale >= 0) { "Scale must be non-negative" }
    }

    companion object {
        /**
         * Create Money from minor units (canonical storage representation).
         */
        fun fromMinorUnits(minorUnits: Long, currencyCode: String): Money =
            Money(minorUnits, currencyCode, CurrencyMetadata.getScale(currencyCode))

        /**
         * Parse a user-entered decimal string (e.g., "12.34") into minor units.
         *
         * IMPORTANT: Do not use `Double`/`Float` for parsing or construction.
         * Parsing must be deterministic across platforms and stable for persistence.
         */
        fun parseMajor(
            amountString: String,
            currencyCode: String,
            roundingMode: RoundingMode = RoundingMode.HALF_UP
        ): Money {
            val scale = CurrencyMetadata.getScale(currencyCode)
            val minorUnits = MoneyParser.parseToMinorUnits(
                amountString = amountString,
                scale = scale,
                roundingMode = roundingMode
            )
            return Money(minorUnits, currencyCode, scale)
        }

        /**
         * Zero amount for a currency.
         */
        fun zero(currencyCode: String): Money {
            return Money(0L, currencyCode)
        }
    }

    /**
     * Convert to a major-units string (e.g., "12.34") for display/export.
     * Locale/currency-symbol formatting belongs in platform UI utilities.
     */
    fun toMajorString(): String = MoneyFormatter.toMajorString(this)

    // Arithmetic operations

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return copy(minorUnits = minorUnits + other.minorUnits)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return copy(minorUnits = minorUnits - other.minorUnits)
    }

    operator fun times(multiplier: Int): Money {
        return copy(minorUnits = minorUnits * multiplier)
    }

    operator fun times(multiplier: Long): Money {
        return copy(minorUnits = minorUnits * multiplier)
    }

    /**
     * Multiply by a fixed-point rate expressed in basis points (1/10,000).
     * Example: 8.5% = 850 bps.
     */
    fun multiplyByBasisPoints(basisPoints: Int, roundingMode: RoundingMode = RoundingMode.HALF_UP): Money {
        require(basisPoints >= 0) { "basisPoints must be non-negative" }

        val numerator = minorUnits * basisPoints.toLong()
        val quotient = numerator / 10_000L
        val remainder = numerator % 10_000L

        val rounded = when (roundingMode) {
            RoundingMode.DOWN -> quotient
            RoundingMode.UP -> if (remainder == 0L) quotient else quotient + 1
            RoundingMode.HALF_UP -> if (remainder >= 5_000L) quotient + 1 else quotient
            RoundingMode.HALF_DOWN -> if (remainder > 5_000L) quotient + 1 else quotient
            RoundingMode.HALF_EVEN -> {
                when {
                    remainder > 5_000L -> quotient + 1
                    remainder < 5_000L -> quotient
                    else -> if (quotient % 2L == 0L) quotient else quotient + 1
                }
            }
        }

        return copy(minorUnits = rounded)
    }

    operator fun div(divisor: Int): Money {
        return copy(minorUnits = minorUnits / divisor)
    }

    /**
     * Divide with remainder handling for splits.
     */
    fun divideWithRemainder(divisor: Int, roundingMode: RoundingMode = RoundingMode.HALF_UP): Pair<Money, Money> {
        val quotient = minorUnits / divisor
        val remainder = minorUnits % divisor
        return Pair(
            copy(minorUnits = quotient),
            copy(minorUnits = remainder)
        )
    }

    operator fun unaryMinus(): Money {
        return copy(minorUnits = -minorUnits)
    }

    fun abs(): Money {
        return copy(minorUnits = minorUnits.absoluteValue)
    }

    fun negate(): Money = -this

    val isPositive: Boolean get() = minorUnits > 0
    val isNegative: Boolean get() = minorUnits < 0
    val isZero: Boolean get() = minorUnits == 0L

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return minorUnits.compareTo(other.minorUnits)
    }

    private fun requireSameCurrency(other: Money) {
        require(currencyCode == other.currencyCode) {
            "Cannot perform operation on different currencies: $currencyCode vs ${other.currencyCode}"
        }
    }
}

enum class RoundingMode {
    HALF_UP, // Standard rounding (0.5 rounds up)
    HALF_DOWN, // 0.5 rounds down
    HALF_EVEN, // Banker's rounding
    DOWN, // Truncate toward zero
    UP // Always round away from zero
}
