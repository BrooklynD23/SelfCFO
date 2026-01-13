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
         * Create Money from a major units value (e.g., dollars).
         */
        fun fromMajorUnits(
            amount: Double,
            currencyCode: String
        ): Money {
            val scale = CurrencyMetadata.getScale(currencyCode)
            val multiplier = 10.0.pow(scale)
            val minorUnits = (amount * multiplier).roundToLong()
            return Money(minorUnits, currencyCode, scale)
        }

        /**
         * Create Money from a string (e.g., "12.34").
         */
        fun parse(
            amountString: String,
            currencyCode: String
        ): Money {
            val amount = amountString.toDoubleOrNull()
                ?: throw IllegalArgumentException("Invalid amount: $amountString")
            return fromMajorUnits(amount, currencyCode)
        }

        /**
         * Zero amount for a currency.
         */
        fun zero(currencyCode: String): Money {
            return Money(0L, currencyCode)
        }
    }

    /**
     * Convert to major units (e.g., dollars).
     * Use only for display, not calculations.
     */
    fun toMajorUnits(): Double {
        return minorUnits / 10.0.pow(scale)
    }

    /**
     * Format for display.
     */
    fun format(
        showCurrency: Boolean = true,
        locale: Locale = Locale.getDefault()
    ): String {
        val formatter = NumberFormat.getCurrencyInstance(locale)
        formatter.currency = Currency.getInstance(currencyCode)
        val formatted = formatter.format(toMajorUnits())
        return if (showCurrency) formatted else formatted.replace(Regex("[^0-9.,\\-]"), "").trim()
    }

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
     * Multiply by a decimal with specified rounding.
     */
    fun multiply(
        multiplier: Double,
        roundingMode: RoundingMode = RoundingMode.HALF_UP
    ): Money {
        val result = minorUnits * multiplier
        val rounded = when (roundingMode) {
            RoundingMode.HALF_UP -> (result + 0.5).toLong()
            RoundingMode.HALF_DOWN -> (result + 0.4999999).toLong()
            RoundingMode.DOWN -> result.toLong()
            RoundingMode.UP -> if (result > result.toLong()) result.toLong() + 1 else result.toLong()
            RoundingMode.HALF_EVEN -> result.roundToLong() // Banker's rounding
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
    fun splitEvenly(
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
     * Split by percentages.
     * @param percentages List of percentages (should sum to 100)
     */
    fun splitByPercent(
        total: Money,
        percentages: List<Double>,
        remainderRecipient: RemainderRecipient = RemainderRecipient.LARGEST
    ): List<Money> {
        require(percentages.isNotEmpty()) { "Must have at least one percentage" }

        // Calculate initial allocations
        val allocations = percentages.map { pct ->
            (total.minorUnits * pct / 100.0).toLong()
        }.toMutableList()

        // Calculate remainder
        val allocated = allocations.sum()
        var remainder = total.minorUnits - allocated

        // Distribute remainder
        when (remainderRecipient) {
            RemainderRecipient.FIRST -> {
                allocations[0] = allocations[0] + remainder
            }
            RemainderRecipient.LAST -> {
                allocations[allocations.lastIndex] = allocations.last() + remainder
            }
            RemainderRecipient.LARGEST -> {
                // Give to the party with largest percentage
                val maxIndex = percentages.indices.maxByOrNull { percentages[it] } ?: 0
                allocations[maxIndex] = allocations[maxIndex] + remainder
            }
        }

        return allocations.map { total.copy(minorUnits = it) }
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

        val totalRatio = ratios.sum()
        val percentages = ratios.map { it.toDouble() / totalRatio * 100 }
        return splitByPercent(total, percentages, remainderRecipient)
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
        val money = Money.fromMajorUnits(12.34, "USD")
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
        val splits = MoneyAllocator.splitEvenly(total, 3)

        assertEquals(3, splits.size)
        assertEquals(334L, splits[0].minorUnits)  // Gets remainder
        assertEquals(333L, splits[1].minorUnits)
        assertEquals(333L, splits[2].minorUnits)
        assertEquals(total.minorUnits, splits.sumOf { it.minorUnits })
    }

    @Test
    fun `JPY has zero decimal places`() {
        val yen = Money.fromMajorUnits(1234.0, "JPY")
        assertEquals(1234L, yen.minorUnits)
        assertEquals(0, yen.scale)
    }

    @Test
    fun `percentage split sums to total`() {
        val total = Money(10000L, "USD")  // $100.00
        val splits = MoneyAllocator.splitByPercent(total, listOf(33.33, 33.33, 33.34))

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
