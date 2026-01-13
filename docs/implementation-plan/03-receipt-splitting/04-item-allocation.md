# 04: Item Allocation

## Overview

Implement item-to-participant assignment with multiple allocation strategies (equal split, custom amounts, percentages).

---

## Implementation Steps

### Step 1: Allocation Models

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/splitting/ItemAllocation.kt
package com.ledgerlens.splitting

import com.ledgerlens.domain.Money
import kotlinx.serialization.Serializable

@Serializable
data class ItemAllocation(
    val id: String,
    val itemId: String,
    val participantId: String,
    val allocationType: AllocationType,
    val share: AllocationShare,
    val amount: Money // Calculated amount for this allocation
)

@Serializable
enum class AllocationType {
    EQUAL,      // Split equally among selected participants
    CUSTOM,     // Custom amount per participant
    PERCENTAGE, // Percentage of item price
    SOLE        // Single owner (100%)
}

@Serializable
sealed class AllocationShare {
    @Serializable
    data class Equal(val participantCount: Int) : AllocationShare()

    @Serializable
    data class Custom(val amountMinorUnits: Long) : AllocationShare()

    @Serializable
    data class Percentage(val percent: Float) : AllocationShare()

    @Serializable
    object Sole : AllocationShare()
}
```

### Step 2: Receipt Item with Allocations

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/splitting/ReceiptItem.kt
package com.ledgerlens.splitting

import com.ledgerlens.domain.Money

data class ReceiptItem(
    val id: String,
    val splitId: String,
    val description: String,
    val unitPrice: Money,
    val quantity: Int,
    val totalPrice: Money,
    val allocations: List<ItemAllocation> = emptyList(),
    val isAllocated: Boolean = false
) {
    /**
     * Check if fully allocated (allocations sum to total).
     */
    fun isFullyAllocated(): Boolean {
        if (allocations.isEmpty()) return false
        val allocatedSum = allocations.sumOf { it.amount.minorUnits }
        return allocatedSum == totalPrice.minorUnits
    }

    /**
     * Get remaining unallocated amount.
     */
    fun getUnallocatedAmount(): Money {
        val allocatedSum = allocations.sumOf { it.amount.minorUnits }
        return Money(totalPrice.minorUnits - allocatedSum, totalPrice.currencyCode)
    }
}
```

### Step 3: Item Allocator

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/splitting/ItemAllocator.kt
package com.ledgerlens.splitting

import com.ledgerlens.domain.Money
import com.ledgerlens.domain.MoneyAllocator

class ItemAllocator(
    private val allocationRepository: AllocationRepository
) {

    /**
     * Allocate item equally among participants.
     * Uses largest-remainder method for fair rounding.
     */
    suspend fun allocateEqual(
        item: ReceiptItem,
        participantIds: List<String>
    ): List<ItemAllocation> {
        if (participantIds.isEmpty()) {
            throw IllegalArgumentException("At least one participant required")
        }

        // Use MoneyAllocator for precise splitting
        val amounts = MoneyAllocator.splitEqual(
            total = item.totalPrice,
            parties = participantIds.size
        )

        val allocations = participantIds.mapIndexed { index, participantId ->
            ItemAllocation(
                id = generateId(),
                itemId = item.id,
                participantId = participantId,
                allocationType = AllocationType.EQUAL,
                share = AllocationShare.Equal(participantIds.size),
                amount = amounts[index]
            )
        }

        // Save allocations
        allocationRepository.saveAllocations(item.id, allocations)

        return allocations
    }

    /**
     * Allocate item to single participant.
     */
    suspend fun allocateSole(
        item: ReceiptItem,
        participantId: String
    ): ItemAllocation {
        val allocation = ItemAllocation(
            id = generateId(),
            itemId = item.id,
            participantId = participantId,
            allocationType = AllocationType.SOLE,
            share = AllocationShare.Sole,
            amount = item.totalPrice
        )

        allocationRepository.saveAllocations(item.id, listOf(allocation))

        return allocation
    }

    /**
     * Allocate item with custom amounts.
     */
    suspend fun allocateCustom(
        item: ReceiptItem,
        customAmounts: Map<String, Long> // participantId -> amount in cents
    ): List<ItemAllocation> {
        val totalAllocated = customAmounts.values.sum()
        if (totalAllocated != item.totalPrice.minorUnits) {
            throw IllegalArgumentException(
                "Custom amounts ($totalAllocated) must equal item price (${item.totalPrice.minorUnits})"
            )
        }

        val allocations = customAmounts.map { (participantId, amount) ->
            ItemAllocation(
                id = generateId(),
                itemId = item.id,
                participantId = participantId,
                allocationType = AllocationType.CUSTOM,
                share = AllocationShare.Custom(amount),
                amount = Money(amount, item.totalPrice.currencyCode)
            )
        }

        allocationRepository.saveAllocations(item.id, allocations)

        return allocations
    }

    /**
     * Allocate item by percentage.
     */
    suspend fun allocateByPercentage(
        item: ReceiptItem,
        percentages: Map<String, Float> // participantId -> percentage (0-100)
    ): List<ItemAllocation> {
        val totalPercent = percentages.values.sum()
        if (kotlin.math.abs(totalPercent - 100f) > 0.01f) {
            throw IllegalArgumentException("Percentages must sum to 100%")
        }

        // Calculate raw amounts
        val rawAmounts = percentages.mapValues { (_, percent) ->
            (item.totalPrice.minorUnits * percent / 100f).toLong()
        }

        // Adjust for rounding (use largest remainder)
        val adjustedAmounts = adjustForRounding(
            rawAmounts = rawAmounts,
            targetTotal = item.totalPrice.minorUnits
        )

        val allocations = adjustedAmounts.map { (participantId, amount) ->
            ItemAllocation(
                id = generateId(),
                itemId = item.id,
                participantId = participantId,
                allocationType = AllocationType.PERCENTAGE,
                share = AllocationShare.Percentage(percentages[participantId]!!),
                amount = Money(amount, item.totalPrice.currencyCode)
            )
        }

        allocationRepository.saveAllocations(item.id, allocations)

        return allocations
    }

    /**
     * Clear allocations for an item.
     */
    suspend fun clearAllocations(itemId: String) {
        allocationRepository.deleteAllocations(itemId)
    }

    /**
     * Get allocations for an item.
     */
    suspend fun getAllocations(itemId: String): List<ItemAllocation> {
        return allocationRepository.getAllocations(itemId)
    }

    /**
     * Quick allocate: assign each item to a participant in order.
     */
    suspend fun quickAllocateRoundRobin(
        items: List<ReceiptItem>,
        participantIds: List<String>
    ): Map<String, List<ItemAllocation>> {
        val result = mutableMapOf<String, List<ItemAllocation>>()

        items.forEachIndexed { index, item ->
            val participantId = participantIds[index % participantIds.size]
            result[item.id] = listOf(allocateSole(item, participantId))
        }

        return result
    }

    /**
     * Quick allocate: split all items equally among all participants.
     */
    suspend fun quickAllocateEqualAll(
        items: List<ReceiptItem>,
        participantIds: List<String>
    ): Map<String, List<ItemAllocation>> {
        val result = mutableMapOf<String, List<ItemAllocation>>()

        for (item in items) {
            result[item.id] = allocateEqual(item, participantIds)
        }

        return result
    }

    private fun adjustForRounding(
        rawAmounts: Map<String, Long>,
        targetTotal: Long
    ): Map<String, Long> {
        val currentTotal = rawAmounts.values.sum()
        val difference = targetTotal - currentTotal

        if (difference == 0L) return rawAmounts

        // Give extra cents to participants with largest remainders
        val withRemainders = rawAmounts.mapValues { (participantId, amount) ->
            val original = percentages[participantId]!! * targetTotal / 100f
            val remainder = original - amount
            Pair(amount, remainder)
        }

        val sorted = withRemainders.entries.sortedByDescending { it.value.second }
        val adjusted = rawAmounts.toMutableMap()

        var remaining = difference
        for (entry in sorted) {
            if (remaining == 0L) break
            if (remaining > 0) {
                adjusted[entry.key] = adjusted[entry.key]!! + 1
                remaining--
            } else {
                adjusted[entry.key] = adjusted[entry.key]!! - 1
                remaining++
            }
        }

        return adjusted
    }
}
```

### Step 4: Allocation Repository

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/data/AllocationRepository.kt
package com.ledgerlens.data

interface AllocationRepository {
    suspend fun saveAllocations(itemId: String, allocations: List<ItemAllocation>)
    suspend fun getAllocations(itemId: String): List<ItemAllocation>
    suspend fun deleteAllocations(itemId: String)
    suspend fun getAllocationsForSplit(splitId: String): List<ItemAllocation>
    suspend fun getAllocationsForParticipant(participantId: String): List<ItemAllocation>
}

// SQLDelight schema
/*
CREATE TABLE ItemAllocation (
    id TEXT PRIMARY KEY NOT NULL,
    item_id TEXT NOT NULL,
    participant_id TEXT NOT NULL,
    allocation_type TEXT NOT NULL,
    share_data TEXT NOT NULL, -- JSON serialized AllocationShare
    amount_minor_units INTEGER NOT NULL,
    currency_code TEXT NOT NULL,
    FOREIGN KEY (item_id) REFERENCES ReceiptItem(id) ON DELETE CASCADE,
    FOREIGN KEY (participant_id) REFERENCES Participant(id) ON DELETE CASCADE
);

CREATE INDEX idx_allocation_item ON ItemAllocation(item_id);
CREATE INDEX idx_allocation_participant ON ItemAllocation(participant_id);
*/
```

### Step 5: Allocation UI Helper

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/splitting/AllocationHelper.kt
package com.ledgerlens.splitting

import com.ledgerlens.domain.Money

/**
 * Helper for allocation UI operations.
 */
class AllocationHelper(
    private val itemAllocator: ItemAllocator
) {

    /**
     * Get allocation summary for display.
     */
    fun summarizeAllocations(
        item: ReceiptItem
    ): AllocationSummary {
        if (item.allocations.isEmpty()) {
            return AllocationSummary(
                status = AllocationStatus.UNALLOCATED,
                participantSummaries = emptyList(),
                description = "Not allocated"
            )
        }

        val participantSummaries = item.allocations.map { allocation ->
            ParticipantShare(
                participantId = allocation.participantId,
                amount = allocation.amount,
                percentage = (allocation.amount.minorUnits.toFloat() / item.totalPrice.minorUnits * 100)
            )
        }

        val status = if (item.isFullyAllocated()) {
            AllocationStatus.FULLY_ALLOCATED
        } else {
            AllocationStatus.PARTIALLY_ALLOCATED
        }

        val description = when {
            item.allocations.size == 1 -> "Single owner"
            item.allocations.all { it.allocationType == AllocationType.EQUAL } ->
                "Split ${item.allocations.size} ways"
            else -> "${item.allocations.size} participants"
        }

        return AllocationSummary(
            status = status,
            participantSummaries = participantSummaries,
            description = description
        )
    }

    /**
     * Toggle participant for equal split.
     */
    suspend fun toggleParticipant(
        item: ReceiptItem,
        participantId: String,
        currentParticipants: Set<String>
    ): List<ItemAllocation> {
        val newParticipants = if (participantId in currentParticipants) {
            currentParticipants - participantId
        } else {
            currentParticipants + participantId
        }

        if (newParticipants.isEmpty()) {
            itemAllocator.clearAllocations(item.id)
            return emptyList()
        }

        return itemAllocator.allocateEqual(item, newParticipants.toList())
    }
}

data class AllocationSummary(
    val status: AllocationStatus,
    val participantSummaries: List<ParticipantShare>,
    val description: String
)

data class ParticipantShare(
    val participantId: String,
    val amount: Money,
    val percentage: Float
)

enum class AllocationStatus {
    UNALLOCATED,
    PARTIALLY_ALLOCATED,
    FULLY_ALLOCATED
}
```

---

## Acceptance Criteria

- [ ] Equal split among 2+ participants works
- [ ] Sole allocation (100% to one person) works
- [ ] Custom amounts allocation works
- [ ] Percentage-based allocation works
- [ ] Largest-remainder method prevents rounding errors
- [ ] Allocations always sum exactly to item total
- [ ] Toggle participant for equal splits
- [ ] Clear allocations works
- [ ] Quick allocate (round-robin, all-equal) works

---

## Testing

### Unit Tests
```kotlin
class ItemAllocatorTest {
    @Test
    fun `equal split of $10 between 3 people`() {
        val item = createItem(totalCents = 1000) // $10.00
        val allocations = allocator.allocateEqual(item, listOf("a", "b", "c"))

        assertEquals(3, allocations.size)
        // Should be 333, 333, 334 (not 333, 333, 333)
        val amounts = allocations.map { it.amount.minorUnits }
        assertEquals(1000, amounts.sum())
        assertTrue(amounts.any { it == 334L })
    }

    @Test
    fun `sole allocation assigns full amount`() {
        val item = createItem(totalCents = 1500)
        val allocation = allocator.allocateSole(item, "alice")

        assertEquals(1500L, allocation.amount.minorUnits)
    }

    @Test
    fun `custom amounts must equal item total`() {
        val item = createItem(totalCents = 1000)

        assertThrows<IllegalArgumentException> {
            allocator.allocateCustom(item, mapOf("a" to 600L, "b" to 300L)) // Only 900
        }
    }

    @Test
    fun `percentage allocation with rounding`() {
        val item = createItem(totalCents = 1000)
        val allocations = allocator.allocateByPercentage(
            item,
            mapOf("a" to 33.33f, "b" to 33.33f, "c" to 33.34f)
        )

        assertEquals(1000L, allocations.sumOf { it.amount.minorUnits })
    }
}
```

---

## Estimated Complexity

**Medium** - Core allocation logic with multiple strategies.
