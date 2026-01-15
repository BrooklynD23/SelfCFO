# 03: Money Type

## Overview

Implement the Money type per [ADR-006](../../PRDs/13-architecture-decision-records.md#adr-006-money-type-representation) using integer minor units for precise financial calculations.

---

## Implementation Steps

### Step 1: Define Money Data Class

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/domain/Money.kt
package com.ledgerlens.domain

import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.roundToLong

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
    fun multiplyByBasisPoints(
        basisPoints: Int,
        roundingMode: RoundingMode = RoundingMode.HALF_UP
    ): Money {
        require(basisPoints >= 0) { "basisPoints must be non-negative" }

        val numerator = minorUnits * basisPoints.toLong() // NOTE: keep inputs bounded to avoid overflow.
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
    fun divideWithRemainder(
        divisor: Int,
        roundingMode: RoundingMode = RoundingMode.HALF_UP
    ): Pair<Money, Money> {
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
    HALF_UP,      // Standard rounding (0.5 rounds up)
    HALF_DOWN,    // 0.5 rounds down
    HALF_EVEN,    // Banker's rounding
    DOWN,         // Truncate toward zero
    UP            // Always round away from zero
}
```

### Step 1b: Parsing & Formatting Utilities (No Floating Point)

Keep the canonical `Money` type free of locale/JVM dependencies. Provide:
- `MoneyParser` for deterministic parsing from user/import strings
- `MoneyFormatter` for canonical major-units strings (no locale)
- Platform/UI formatting in a separate `expect/actual` formatter

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/domain/MoneyParser.kt
package com.ledgerlens.domain

object MoneyParser {
    /**
     * Parse a decimal string into minor units using integer math.
     *
     * Supported inputs (examples):
     * - "12.34"
     * - "-12.34"
     * - "(12.34)"   // accounting negative
     * - "1,234.56"  // thousands separators
     *
     * NOTE: If more fractional digits are provided than the currency `scale`,
     * apply `roundingMode` deterministically.
     */
    fun parseToMinorUnits(
        amountString: String,
        scale: Int,
        roundingMode: RoundingMode = RoundingMode.HALF_UP
    ): Long {
        require(scale >= 0) { "scale must be non-negative" }

        var text = amountString.trim()
        require(text.isNotBlank()) { "amountString is blank" }

        // Accounting negatives: "(12.34)"
        var negative = false
        if (text.startsWith("(") && text.endsWith(")")) {
            negative = true
            text = text.substring(1, text.length - 1).trim()
        }

        // Leading sign
        if (text.startsWith("+")) text = text.drop(1).trim()
        if (text.startsWith("-")) {
            negative = true
            text = text.drop(1).trim()
        }

        // Keep digits and separators only; other characters (currency symbols, spaces) are ignored.
        text = text.replace(Regex("""[^0-9.,]"""), "")
        require(text.isNotBlank()) { "No digits found in amountString" }

        val lastDot = text.lastIndexOf('.')
        val lastComma = text.lastIndexOf(',')

        val decimalSep: Char? = when {
            lastDot >= 0 && lastComma >= 0 -> if (lastDot > lastComma) '.' else ','
            lastDot >= 0 -> '.'
            lastComma >= 0 -> {
                // Heuristic: treat comma as decimal only if it looks like a fractional separator.
                val digitsAfter = text.length - lastComma - 1
                if (digitsAfter in 1..maxOf(scale, 1)) ',' else null
            }
            else -> null
        }

        val groupingSep: Char? = when (decimalSep) {
            '.' -> ','
            ',' -> '.'
            else -> ','
        }

        val (wholeRaw, fracRaw) = if (decimalSep != null && text.contains(decimalSep)) {
            val parts = text.split(decimalSep, limit = 2)
            parts[0] to parts.getOrElse(1) { "" }
        } else {
            text to ""
        }

        val wholeDigits = wholeRaw.replace(groupingSep.toString(), "").ifBlank { "0" }
        val fracDigits = fracRaw.replace(groupingSep.toString(), "")

        val whole = wholeDigits.toLongOrNull()
            ?: throw IllegalArgumentException("Invalid whole part: $wholeDigits")

        fun pow10Long(exp: Int): Long = (1..exp).fold(1L) { acc, _ -> acc * 10L }
        val factor = pow10Long(scale)

        val keptFrac = when {
            scale == 0 -> ""
            fracDigits.length <= scale -> fracDigits.padEnd(scale, '0')
            else -> fracDigits.substring(0, scale)
        }
        val baseFrac = if (keptFrac.isBlank()) 0L else keptFrac.toLong()

        var minorUnits = whole * factor + baseFrac

        // Rounding if extra fractional digits exist
        if (fracDigits.length > scale) {
            val nextDigit = fracDigits.getOrNull(scale)?.digitToIntOrNull() ?: 0
            val rest = fracDigits.drop(scale + 1)
            val restNonZero = rest.any { it != '0' }

            val roundUp = when (roundingMode) {
                RoundingMode.DOWN -> false
                RoundingMode.UP -> nextDigit != 0 || restNonZero
                RoundingMode.HALF_UP -> nextDigit >= 5
                RoundingMode.HALF_DOWN -> nextDigit > 5 || (nextDigit == 5 && restNonZero)
                RoundingMode.HALF_EVEN -> when {
                    nextDigit > 5 -> true
                    nextDigit < 5 -> false
                    restNonZero -> true
                    else -> (minorUnits % 2L) != 0L
                }
            }

            if (roundUp) minorUnits += 1L
        }

        return if (negative) -minorUnits else minorUnits
    }
}

// shared/src/commonMain/kotlin/com/ledgerlens/domain/MoneyFormatter.kt
package com.ledgerlens.domain

object MoneyFormatter {
    /**
     * Canonical major-units string (e.g., "12.34") for export/debugging.
     * Locale/currency symbol formatting belongs in platform UI.
     */
    fun toMajorString(money: Money): String {
        val sign = if (money.minorUnits < 0) "-" else ""
        val abs = kotlin.math.abs(money.minorUnits)

        val scale = money.scale
        if (scale == 0) return sign + abs.toString()

        val divisor = (1L..scale).fold(1L) { acc, _ -> acc * 10L }
        val whole = abs / divisor
        val frac = abs % divisor
        return sign + whole.toString() + "." + frac.toString().padStart(scale, '0')
    }
}

// shared/src/commonMain/kotlin/com/ledgerlens/domain/MoneyLocaleFormatter.kt
package com.ledgerlens.domain

/**
 * Platform/UI-specific formatting (symbols, grouping, locale rules).
 */
expect object MoneyLocaleFormatter {
    fun format(money: Money, showCurrency: Boolean = true): String
}

/**
 * Convenience for app/UI code.
 */
fun Money.formatForDisplay(showCurrency: Boolean = true): String =
    MoneyLocaleFormatter.format(this, showCurrency)
```

### Step 2: Currency Metadata

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/domain/CurrencyMetadata.kt
package com.ledgerlens.domain

object CurrencyMetadata {
    private val scales = mapOf(
        // Major currencies
        "USD" to 2,
        "EUR" to 2,
        "GBP" to 2,
        "CAD" to 2,
        "AUD" to 2,
        "CHF" to 2,
        "CNY" to 2,
        "INR" to 2,
        "MXN" to 2,
        "BRL" to 2,

        // Zero decimal currencies
        "JPY" to 0,
        "KRW" to 0,
        "VND" to 0,

        // Three decimal currencies
        "BHD" to 3,
        "KWD" to 3,
        "OMR" to 3,

        // Cryptocurrencies (common representations)
        "BTC" to 8,
        "ETH" to 18,
        "USDC" to 6,
        "USDT" to 6,
    )

    private val symbols = mapOf(
        "USD" to "$",
        "EUR" to "€",
        "GBP" to "£",
        "JPY" to "¥",
        "CNY" to "¥",
        "INR" to "₹",
        "BTC" to "₿",
    )

    fun getScale(currencyCode: String): Int {
        return scales[currencyCode.uppercase()] ?: 2 // Default to 2 decimal places
    }

    fun getSymbol(currencyCode: String): String {
        return symbols[currencyCode.uppercase()] ?: currencyCode
    }

    fun isValidCurrency(currencyCode: String): Boolean {
        return currencyCode.length == 3 && currencyCode.all { it.isLetter() }
    }
}
```

### Step 3: Money Allocation for Splits

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/domain/MoneyAllocator.kt
package com.ledgerlens.domain

/**
 * Allocates money across multiple parties with proper remainder handling.
 * Ensures the sum of allocations exactly equals the original amount.
 */
object MoneyAllocator {

    /**
     * Split evenly among N parties.
     * Remainder goes to the first party (typically the payer).
     */
    fun splitEqual(
        total: Money,
        parties: Int,
        remainderRecipient: RemainderRecipient = RemainderRecipient.FIRST
    ): List<Money> {
        require(parties > 0) { "Must have at least one party" }

        val baseAmount = total.minorUnits / parties
        val remainder = (total.minorUnits % parties).toInt()

        return List(parties) { index ->
            val extra = when (remainderRecipient) {
                RemainderRecipient.FIRST -> if (index < remainder) 1L else 0L
                RemainderRecipient.LAST -> if (index >= parties - remainder) 1L else 0L
                RemainderRecipient.LARGEST -> if (index < remainder) 1L else 0L // Same as FIRST for even split
            }
            total.copy(minorUnits = baseAmount + extra)
        }
    }

    /**
     * Split by fixed-point percentages in basis points (1/10,000).
     * Example: 33.33% = 3333 bps.
     *
     * @param percentagesBps List of basis points (should sum to 10_000)
     */
    fun splitByPercentBps(
        total: Money,
        percentagesBps: List<Int>,
        remainderRecipient: RemainderRecipient = RemainderRecipient.LARGEST
    ): List<Money> {
        require(percentagesBps.isNotEmpty()) { "Must have at least one percentage" }
        require(percentagesBps.all { it >= 0 }) { "Percentages must be non-negative" }
        require(percentagesBps.sum() == 10_000) { "Percentages must sum to 10,000 bps (100.00%)" }

        // Integer-only allocation:
        //   floor(total * bps / 10_000) with largest-remainder distribution.
        val baseAllocations = mutableListOf<Long>()
        val remainders = mutableListOf<Long>()

        for (bps in percentagesBps) {
            val numerator = total.minorUnits * bps.toLong() // Keep inputs bounded to avoid overflow.
            baseAllocations += numerator / 10_000L
            remainders += numerator % 10_000L
        }

        var allocated = baseAllocations.sum()
        var remainingCents = total.minorUnits - allocated

        if (remainingCents > 0) {
            val indicesByRemainder = remainders.indices.sortedByDescending { remainders[it] }
            var i = 0
            while (remainingCents > 0 && i < indicesByRemainder.size) {
                val idx = indicesByRemainder[i]
                baseAllocations[idx] = baseAllocations[idx] + 1
                remainingCents--
                i++
            }
        }

        if (remainingCents != 0L) {
            // As a deterministic fallback, assign any unexpected remainder per requested strategy.
            when (remainderRecipient) {
                RemainderRecipient.FIRST -> baseAllocations[0] = baseAllocations[0] + remainingCents
                RemainderRecipient.LAST -> baseAllocations[baseAllocations.lastIndex] =
                    baseAllocations.last() + remainingCents
                RemainderRecipient.LARGEST -> {
                    val maxIndex = percentagesBps.indices.maxByOrNull { percentagesBps[it] } ?: 0
                    baseAllocations[maxIndex] = baseAllocations[maxIndex] + remainingCents
                }
            }
        }

        return baseAllocations.map { total.copy(minorUnits = it) }
    }

    /**
     * Split by explicit ratios.
     * @param ratios Integer ratios (e.g., [1, 2, 1] for 25%, 50%, 25%)
     */
    fun splitByRatio(
        total: Money,
        ratios: List<Int>,
        remainderRecipient: RemainderRecipient = RemainderRecipient.LARGEST
    ): List<Money> {
        require(ratios.isNotEmpty()) { "Must have at least one ratio" }
        require(ratios.all { it > 0 }) { "All ratios must be positive" }

        val totalRatio = ratios.sum().toLong()
        val weights = ratios.map { it.toLong() }
        return splitByWeights(total, weights, totalRatio, remainderRecipient)
    }

    /**
     * Split by integer weights.
     *
     * This is the primitive used for proportional fee allocation and other “share of total” cases.
     */
    fun splitByWeights(
        total: Money,
        weights: List<Long>,
        totalWeight: Long = weights.sum(),
        remainderRecipient: RemainderRecipient = RemainderRecipient.LARGEST
    ): List<Money> {
        require(weights.isNotEmpty()) { "Must have at least one weight" }
        require(weights.all { it >= 0L }) { "Weights must be non-negative" }
        require(totalWeight > 0L) { "Total weight must be positive" }

        val baseAllocations = mutableListOf<Long>()
        val remainders = mutableListOf<Long>()

        for (w in weights) {
            val numerator = total.minorUnits * w // Keep inputs bounded to avoid overflow.
            baseAllocations += numerator / totalWeight
            remainders += numerator % totalWeight
        }

        var remainingCents = total.minorUnits - baseAllocations.sum()
        if (remainingCents > 0) {
            val indicesByRemainder = remainders.indices.sortedByDescending { remainders[it] }
            var i = 0
            while (remainingCents > 0 && i < indicesByRemainder.size) {
                val idx = indicesByRemainder[i]
                baseAllocations[idx] = baseAllocations[idx] + 1
                remainingCents--
                i++
            }
        }

        if (remainingCents != 0L) {
            when (remainderRecipient) {
                RemainderRecipient.FIRST -> baseAllocations[0] = baseAllocations[0] + remainingCents
                RemainderRecipient.LAST -> baseAllocations[baseAllocations.lastIndex] =
                    baseAllocations.last() + remainingCents
                RemainderRecipient.LARGEST -> {
                    val maxIndex = weights.indices.maxByOrNull { weights[it] } ?: 0
                    baseAllocations[maxIndex] = baseAllocations[maxIndex] + remainingCents
                }
            }
        }

        return baseAllocations.map { total.copy(minorUnits = it) }
    }

    enum class RemainderRecipient {
        FIRST,    // First party gets remainder
        LAST,     // Last party gets remainder
        LARGEST   // Party with largest share gets remainder
    }
}
```

### Step 4: Money Serialization

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/domain/MoneySerializer.kt
package com.ledgerlens.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = MoneySerializer::class)
data class SerializableMoney(
    val minorUnits: Long,
    val currencyCode: String,
    val scale: Int
) {
    fun toMoney(): Money = Money(minorUnits, currencyCode, scale)

    companion object {
        fun fromMoney(money: Money) = SerializableMoney(
            money.minorUnits,
            money.currencyCode,
            money.scale
        )
    }
}

object MoneySerializer : KSerializer<Money> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Money") {
        element("minorUnits", Long.serializer().descriptor)
        element("currencyCode", String.serializer().descriptor)
        element("scale", Int.serializer().descriptor)
    }

    override fun serialize(encoder: Encoder, value: Money) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeLongElement(descriptor, 0, value.minorUnits)
        composite.encodeStringElement(descriptor, 1, value.currencyCode)
        composite.encodeIntElement(descriptor, 2, value.scale)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): Money {
        // Implementation
    }
}
```

### Step 5: Unit Tests

```kotlin
// shared/src/commonTest/kotlin/com/ledgerlens/domain/MoneyTest.kt
package com.ledgerlens.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MoneyTest {

    @Test
    fun `create from major units`() {
        val money = Money.parseMajor("12.34", "USD")
        assertEquals(1234L, money.minorUnits)
        assertEquals("USD", money.currencyCode)
        assertEquals(2, money.scale)
    }

    @Test
    fun `addition works correctly`() {
        val a = Money(1234L, "USD")
        val b = Money(567L, "USD")
        val result = a + b
        assertEquals(1801L, result.minorUnits)
    }

    @Test
    fun `split evenly with remainder`() {
        val total = Money(1000L, "USD")  // $10.00
        val splits = MoneyAllocator.splitEqual(total, 3)

        assertEquals(3, splits.size)
        assertEquals(334L, splits[0].minorUnits)  // Gets remainder
        assertEquals(333L, splits[1].minorUnits)
        assertEquals(333L, splits[2].minorUnits)
        assertEquals(total.minorUnits, splits.sumOf { it.minorUnits })
    }

    @Test
    fun `JPY has zero decimal places`() {
        val yen = Money.parseMajor("1234", "JPY")
        assertEquals(1234L, yen.minorUnits)
        assertEquals(0, yen.scale)
    }

    @Test
    fun `percentage split sums to total`() {
        val total = Money(10000L, "USD")  // $100.00
        val splits = MoneyAllocator.splitByPercentBps(total, listOf(3333, 3333, 3334))

        assertEquals(total.minorUnits, splits.sumOf { it.minorUnits })
    }
}
```

---

## Acceptance Criteria

- [ ] Money class with integer minor units
- [ ] All arithmetic operations work correctly
- [ ] Currency metadata for common currencies
- [ ] Rounding modes implemented
- [ ] Split allocation with remainder handling
- [ ] Serialization works
- [ ] Unit tests pass with edge cases

---

## Dependencies

- Kotlinx Serialization

---

## Estimated Complexity

**Medium** - Well-defined requirements with comprehensive testing needed.
