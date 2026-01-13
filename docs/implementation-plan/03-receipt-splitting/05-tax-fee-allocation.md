# 05: Tax & Fee Allocation

## Overview

Implement distribution of taxes, tips, and service fees proportionally based on item allocations.

---

## Implementation Steps

### Step 1: Fee Types

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/splitting/ReceiptFees.kt
package com.ledgerlens.splitting

import com.ledgerlens.domain.Money
import kotlinx.serialization.Serializable

@Serializable
data class ReceiptFees(
    val tax: Money?,
    val tip: Money?,
    val serviceFee: Money?,
    val otherFees: List<NamedFee> = emptyList()
) {
    val total: Money
        get() {
            val currency = tax?.currencyCode ?: tip?.currencyCode ?: "USD"
            val sum = listOfNotNull(tax, tip, serviceFee).sumOf { it.minorUnits } +
                    otherFees.sumOf { it.amount.minorUnits }
            return Money(sum, currency)
        }
}

@Serializable
data class NamedFee(
    val name: String,
    val amount: Money
)

@Serializable
enum class FeeDistributionMethod {
    PROPORTIONAL,    // Based on share of subtotal
    EQUAL,           // Split equally
    ITEMS_ONLY,      // Only among participants who have items
    CUSTOM           // Manual assignment
}
```

### Step 2: Tax Allocator

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/splitting/TaxAllocator.kt
package com.ledgerlens.splitting

import com.ledgerlens.domain.Money
import com.ledgerlens.domain.MoneyAllocator

class TaxAllocator {

    /**
     * Distribute fees proportionally based on item subtotals.
     * Each participant pays fee proportional to their share of items.
     */
    fun allocateProportional(
        fee: Money,
        participantSubtotals: Map<String, Money> // participantId -> their item subtotal
    ): Map<String, Money> {
        if (participantSubtotals.isEmpty()) {
            return emptyMap()
        }

        val totalSubtotal = participantSubtotals.values.sumOf { it.minorUnits }
        if (totalSubtotal == 0L) {
            // If no subtotals, split equally
            return allocateEqual(fee, participantSubtotals.keys.toList())
        }

        // Calculate proportions
        val proportions = participantSubtotals.mapValues { (_, subtotal) ->
            subtotal.minorUnits.toDouble() / totalSubtotal.toDouble()
        }

        // Allocate with largest-remainder method
        return allocateByProportions(fee, proportions)
    }

    /**
     * Split fee equally among participants.
     */
    fun allocateEqual(
        fee: Money,
        participantIds: List<String>
    ): Map<String, Money> {
        if (participantIds.isEmpty()) return emptyMap()

        val amounts = MoneyAllocator.splitEqual(fee, participantIds.size)

        return participantIds.mapIndexed { index, id ->
            id to amounts[index]
        }.toMap()
    }

    /**
     * Allocate only among participants who ordered items.
     */
    fun allocateAmongItemHolders(
        fee: Money,
        participantSubtotals: Map<String, Money>
    ): Map<String, Money> {
        val withItems = participantSubtotals.filter { it.value.minorUnits > 0 }

        if (withItems.isEmpty()) {
            return allocateEqual(fee, participantSubtotals.keys.toList())
        }

        return allocateProportional(fee, withItems)
    }

    /**
     * Custom allocation with specific amounts.
     */
    fun allocateCustom(
        fee: Money,
        customAmounts: Map<String, Long>
    ): Map<String, Money> {
        val total = customAmounts.values.sum()
        if (total != fee.minorUnits) {
            throw IllegalArgumentException(
                "Custom amounts ($total) must equal fee (${fee.minorUnits})"
            )
        }

        return customAmounts.mapValues { (_, amount) ->
            Money(amount, fee.currencyCode)
        }
    }

    private fun allocateByProportions(
        total: Money,
        proportions: Map<String, Double>
    ): Map<String, Money> {
        // Calculate raw amounts
        val rawAmounts = proportions.mapValues { (_, proportion) ->
            (total.minorUnits * proportion).toLong()
        }

        // Apply largest-remainder adjustment
        val allocated = rawAmounts.values.sum()
        val remainder = total.minorUnits - allocated

        if (remainder == 0L) {
            return rawAmounts.mapValues { Money(it.value, total.currencyCode) }
        }

        // Sort by decimal remainder (descending) to determine who gets extra cents
        val withRemainders = proportions.mapValues { (_, proportion) ->
            val exact = total.minorUnits * proportion
            val truncated = exact.toLong()
            exact - truncated
        }

        val sorted = withRemainders.entries.sortedByDescending { it.value }
        val adjusted = rawAmounts.toMutableMap()

        var remaining = remainder
        for (entry in sorted) {
            if (remaining <= 0) break
            adjusted[entry.key] = adjusted[entry.key]!! + 1
            remaining--
        }

        return adjusted.mapValues { Money(it.value, total.currencyCode) }
    }
}
```

### Step 3: Fee Distribution Service

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/splitting/FeeDistributionService.kt
package com.ledgerlens.splitting

import com.ledgerlens.domain.Money

class FeeDistributionService(
    private val taxAllocator: TaxAllocator,
    private val itemAllocator: ItemAllocator
) {

    /**
     * Distribute all fees for a receipt split.
     */
    suspend fun distributeFees(
        split: ReceiptSplit,
        fees: ReceiptFees,
        method: FeeDistributionMethod = FeeDistributionMethod.PROPORTIONAL
    ): FeeDistribution {
        // Calculate each participant's item subtotal
        val participantSubtotals = calculateParticipantSubtotals(split)

        val taxAllocation = fees.tax?.let { tax ->
            allocateFee(tax, participantSubtotals, method)
        } ?: emptyMap()

        val tipAllocation = fees.tip?.let { tip ->
            allocateFee(tip, participantSubtotals, method)
        } ?: emptyMap()

        val serviceFeeAllocation = fees.serviceFee?.let { serviceFee ->
            allocateFee(serviceFee, participantSubtotals, method)
        } ?: emptyMap()

        val otherFeeAllocations = fees.otherFees.associate { namedFee ->
            namedFee.name to allocateFee(namedFee.amount, participantSubtotals, method)
        }

        return FeeDistribution(
            taxByParticipant = taxAllocation,
            tipByParticipant = tipAllocation,
            serviceFeeByParticipant = serviceFeeAllocation,
            otherFeesByParticipant = otherFeeAllocations
        )
    }

    /**
     * Calculate tip based on percentage.
     */
    fun calculateTip(
        subtotal: Money,
        percentage: Float
    ): Money {
        val tipAmount = (subtotal.minorUnits * percentage / 100f).toLong()
        return Money(tipAmount, subtotal.currencyCode)
    }

    /**
     * Calculate tip suggestions.
     */
    fun getTipSuggestions(subtotal: Money): List<TipSuggestion> {
        return listOf(
            TipSuggestion(15f, calculateTip(subtotal, 15f)),
            TipSuggestion(18f, calculateTip(subtotal, 18f)),
            TipSuggestion(20f, calculateTip(subtotal, 20f)),
            TipSuggestion(25f, calculateTip(subtotal, 25f))
        )
    }

    /**
     * Round tip to nice number.
     */
    fun roundTip(tip: Money): Money {
        // Round to nearest 50 cents
        val rounded = ((tip.minorUnits + 25) / 50) * 50
        return Money(rounded, tip.currencyCode)
    }

    private suspend fun calculateParticipantSubtotals(
        split: ReceiptSplit
    ): Map<String, Money> {
        val subtotals = mutableMapOf<String, Long>()
        val currency = split.currency

        for (item in split.items) {
            val allocations = itemAllocator.getAllocations(item.id)
            for (allocation in allocations) {
                subtotals[allocation.participantId] =
                    (subtotals[allocation.participantId] ?: 0L) + allocation.amount.minorUnits
            }
        }

        return subtotals.mapValues { (_, amount) -> Money(amount, currency) }
    }

    private fun allocateFee(
        fee: Money,
        participantSubtotals: Map<String, Money>,
        method: FeeDistributionMethod
    ): Map<String, Money> {
        return when (method) {
            FeeDistributionMethod.PROPORTIONAL ->
                taxAllocator.allocateProportional(fee, participantSubtotals)

            FeeDistributionMethod.EQUAL ->
                taxAllocator.allocateEqual(fee, participantSubtotals.keys.toList())

            FeeDistributionMethod.ITEMS_ONLY ->
                taxAllocator.allocateAmongItemHolders(fee, participantSubtotals)

            FeeDistributionMethod.CUSTOM ->
                throw IllegalArgumentException("Use allocateCustom for custom distribution")
        }
    }
}

data class FeeDistribution(
    val taxByParticipant: Map<String, Money>,
    val tipByParticipant: Map<String, Money>,
    val serviceFeeByParticipant: Map<String, Money>,
    val otherFeesByParticipant: Map<String, Map<String, Money>>
) {
    /**
     * Get total fees for a participant.
     */
    fun getTotalForParticipant(participantId: String): Money {
        val tax = taxByParticipant[participantId]?.minorUnits ?: 0L
        val tip = tipByParticipant[participantId]?.minorUnits ?: 0L
        val serviceFee = serviceFeeByParticipant[participantId]?.minorUnits ?: 0L
        val otherFees = otherFeesByParticipant.values.sumOf {
            it[participantId]?.minorUnits ?: 0L
        }

        val currency = taxByParticipant.values.firstOrNull()?.currencyCode ?: "USD"
        return Money(tax + tip + serviceFee + otherFees, currency)
    }
}

data class TipSuggestion(
    val percentage: Float,
    val amount: Money
)
```

### Step 4: Fee Input Service

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/splitting/FeeInputService.kt
package com.ledgerlens.splitting

import com.ledgerlens.domain.Money

/**
 * Handle fee input and detection.
 */
class FeeInputService {

    /**
     * Extract fees from parsed receipt.
     */
    fun extractFees(receipt: ParsedReceipt): ReceiptFees {
        return ReceiptFees(
            tax = receipt.tax,
            tip = receipt.tip,
            serviceFee = null,
            otherFees = emptyList()
        )
    }

    /**
     * Manually set fees.
     */
    fun setFees(
        tax: Long? = null,
        tip: Long? = null,
        serviceFee: Long? = null,
        otherFees: List<Pair<String, Long>> = emptyList(),
        currency: String = "USD"
    ): ReceiptFees {
        return ReceiptFees(
            tax = tax?.let { Money(it, currency) },
            tip = tip?.let { Money(it, currency) },
            serviceFee = serviceFee?.let { Money(it, currency) },
            otherFees = otherFees.map { (name, amount) ->
                NamedFee(name, Money(amount, currency))
            }
        )
    }

    /**
     * Calculate tax from rate.
     */
    fun calculateTax(
        subtotal: Money,
        taxRate: Float // e.g., 8.5 for 8.5%
    ): Money {
        val taxAmount = (subtotal.minorUnits * taxRate / 100f).toLong()
        return Money(taxAmount, subtotal.currencyCode)
    }

    /**
     * Get common tax rates.
     */
    fun getCommonTaxRates(): List<TaxRate> {
        return listOf(
            TaxRate("No Tax", 0f),
            TaxRate("6%", 6f),
            TaxRate("7%", 7f),
            TaxRate("8%", 8f),
            TaxRate("8.5%", 8.5f),
            TaxRate("9%", 9f),
            TaxRate("10%", 10f)
        )
    }

    /**
     * Auto-detect tax rate from receipt.
     */
    fun detectTaxRate(
        subtotal: Money,
        tax: Money
    ): Float? {
        if (subtotal.minorUnits == 0L) return null
        val rate = tax.minorUnits.toFloat() / subtotal.minorUnits * 100
        // Round to nearest 0.25%
        return (rate * 4).toInt() / 4f
    }
}

data class TaxRate(
    val label: String,
    val rate: Float
)
```

---

## Acceptance Criteria

- [ ] Tax distributed proportionally by item share
- [ ] Tip distributed proportionally by item share
- [ ] Service fees distributed correctly
- [ ] Equal distribution option works
- [ ] Items-only distribution excludes non-orderers
- [ ] Tip suggestions (15%, 18%, 20%, 25%) provided
- [ ] Tax rate auto-detection works
- [ ] Custom fee amounts supported
- [ ] Fee totals always match input amounts (no rounding loss)

---

## Testing

### Unit Tests
```kotlin
class TaxAllocatorTest {
    @Test
    fun `proportional allocation based on subtotals`() {
        val fee = Money(100, "USD") // $1.00
        val subtotals = mapOf(
            "alice" to Money(3000, "USD"), // $30
            "bob" to Money(1000, "USD")    // $10
        )

        val allocation = allocator.allocateProportional(fee, subtotals)

        // Alice: 75%, Bob: 25%
        assertEquals(75L, allocation["alice"]!!.minorUnits)
        assertEquals(25L, allocation["bob"]!!.minorUnits)
    }

    @Test
    fun `equal allocation splits evenly`() {
        val fee = Money(100, "USD")
        val allocation = allocator.allocateEqual(fee, listOf("a", "b", "c"))

        // 33, 33, 34 cents
        assertEquals(100L, allocation.values.sumOf { it.minorUnits })
    }

    @Test
    fun `items-only excludes zero-amount participants`() {
        val fee = Money(100, "USD")
        val subtotals = mapOf(
            "alice" to Money(2000, "USD"),
            "bob" to Money(0, "USD") // Bob ordered nothing
        )

        val allocation = allocator.allocateAmongItemHolders(fee, subtotals)

        assertEquals(100L, allocation["alice"]!!.minorUnits)
        assertFalse(allocation.containsKey("bob"))
    }
}

class FeeDistributionServiceTest {
    @Test
    fun `tip suggestions are correct`() {
        val subtotal = Money(4000, "USD") // $40
        val suggestions = service.getTipSuggestions(subtotal)

        assertEquals(600L, suggestions.find { it.percentage == 15f }!!.amount.minorUnits) // $6
        assertEquals(800L, suggestions.find { it.percentage == 20f }!!.amount.minorUnits) // $8
    }
}
```

---

## Estimated Complexity

**Medium** - Proportional calculations with precise rounding.

