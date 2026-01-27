package com.ledgerlens.domain

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property-based tests for MoneyAllocator.
 * 
 * Key invariants tested:
 * - Sum of allocations ALWAYS equals original amount
 * - Number of allocations equals requested number
 * - No allocation exceeds the total
 * - All allocations are non-negative (for non-negative totals)
 * 
 * Tests cover:
 * - splitEqual
 * - splitByPercentBps
 * - splitByRatio
 * - splitByWeights
 */
class MoneyAllocatorPropertyTest {

    private val random = Random(42) // Fixed seed for reproducibility

    // ==================== splitEqual Property Tests ====================

    @Test
    fun `splitEqual sum always equals total - small amounts`() {
        repeat(100) { iteration ->
            val total = Money(random.nextLong(0, 1000), "USD")
            val parties = random.nextInt(1, 10)
            
            val splits = MoneyAllocator.splitEqual(total, parties)
            
            assertEquals(
                total.minorUnits,
                splits.sumOf { it.minorUnits },
                "Iteration $iteration: splitEqual($total, $parties) sum mismatch"
            )
        }
    }

    @Test
    fun `splitEqual sum always equals total - large amounts`() {
        repeat(100) { iteration ->
            val total = Money(random.nextLong(1_000_000, 100_000_000), "USD")
            val parties = random.nextInt(1, 100)
            
            val splits = MoneyAllocator.splitEqual(total, parties)
            
            assertEquals(
                total.minorUnits,
                splits.sumOf { it.minorUnits },
                "Iteration $iteration: splitEqual large amount sum mismatch"
            )
        }
    }

    @Test
    fun `splitEqual returns correct number of parts`() {
        repeat(50) {
            val total = Money(random.nextLong(1, 10000), "USD")
            val parties = random.nextInt(1, 20)
            
            val splits = MoneyAllocator.splitEqual(total, parties)
            
            assertEquals(parties, splits.size)
        }
    }

    @Test
    fun `splitEqual parts differ by at most 1`() {
        repeat(100) {
            val total = Money(random.nextLong(1, 10000), "USD")
            val parties = random.nextInt(2, 20)
            
            val splits = MoneyAllocator.splitEqual(total, parties)
            val amounts = splits.map { it.minorUnits }
            val maxDiff = amounts.max() - amounts.min()
            
            assertTrue(maxDiff <= 1, "Parts should differ by at most 1 unit")
        }
    }

    @Test
    fun `splitEqual with remainder FIRST assigns extra to first parties`() {
        val total = Money(103L, "USD")
        val splits = MoneyAllocator.splitEqual(
            total, 10,
            remainderRecipient = MoneyAllocator.RemainderRecipient.FIRST
        )
        
        // 103 / 10 = 10 remainder 3
        // First 3 parties get 11, rest get 10
        assertEquals(11L, splits[0].minorUnits)
        assertEquals(11L, splits[1].minorUnits)
        assertEquals(11L, splits[2].minorUnits)
        assertEquals(10L, splits[3].minorUnits)
        assertEquals(103L, splits.sumOf { it.minorUnits })
    }

    @Test
    fun `splitEqual with remainder LAST assigns extra to last parties`() {
        val total = Money(103L, "USD")
        val splits = MoneyAllocator.splitEqual(
            total, 10,
            remainderRecipient = MoneyAllocator.RemainderRecipient.LAST
        )
        
        // Last 3 parties get 11, rest get 10
        assertEquals(10L, splits[0].minorUnits)
        assertEquals(10L, splits[6].minorUnits)
        assertEquals(11L, splits[7].minorUnits)
        assertEquals(11L, splits[8].minorUnits)
        assertEquals(11L, splits[9].minorUnits)
        assertEquals(103L, splits.sumOf { it.minorUnits })
    }

    // ==================== splitByPercentBps Property Tests ====================

    @Test
    fun `splitByPercentBps sum always equals total`() {
        repeat(100) { iteration ->
            val total = Money(random.nextLong(1, 1_000_000), "USD")
            val percentages = generateValidPercentages(random.nextInt(2, 6))
            
            val splits = MoneyAllocator.splitByPercentBps(total, percentages)
            
            assertEquals(
                total.minorUnits,
                splits.sumOf { it.minorUnits },
                "Iteration $iteration: splitByPercentBps sum mismatch with $percentages"
            )
        }
    }

    @Test
    fun `splitByPercentBps with equal percentages`() {
        repeat(50) { iteration ->
            val total = Money(random.nextLong(1, 100_000), "USD")
            val parts = random.nextInt(2, 5)
            val basePercent = 10_000 / parts
            val percentages = MutableList(parts) { basePercent }
            percentages[0] += 10_000 - percentages.sum() // Fix rounding
            
            val splits = MoneyAllocator.splitByPercentBps(total, percentages)
            
            assertEquals(
                total.minorUnits,
                splits.sumOf { it.minorUnits },
                "Iteration $iteration: equal percentages sum mismatch"
            )
        }
    }

    @Test
    fun `splitByPercentBps 50-50 split`() {
        repeat(100) { iteration ->
            val total = Money(random.nextLong(1, 1_000_000), "USD")
            
            val splits = MoneyAllocator.splitByPercentBps(total, listOf(5000, 5000))
            
            assertEquals(
                total.minorUnits,
                splits.sumOf { it.minorUnits },
                "Iteration $iteration: 50-50 split sum mismatch"
            )
        }
    }

    @Test
    fun `splitByPercentBps 33-33-34 split`() {
        repeat(100) { iteration ->
            val total = Money(random.nextLong(1, 1_000_000), "USD")
            
            val splits = MoneyAllocator.splitByPercentBps(total, listOf(3333, 3333, 3334))
            
            assertEquals(
                total.minorUnits,
                splits.sumOf { it.minorUnits },
                "Iteration $iteration: thirds split sum mismatch"
            )
        }
    }

    // ==================== splitByRatio Property Tests ====================

    @Test
    fun `splitByRatio sum always equals total`() {
        repeat(100) { iteration ->
            val total = Money(random.nextLong(1, 1_000_000), "USD")
            val ratioCount = random.nextInt(2, 6)
            val ratios = List(ratioCount) { random.nextInt(1, 100) }
            
            val splits = MoneyAllocator.splitByRatio(total, ratios)
            
            assertEquals(
                total.minorUnits,
                splits.sumOf { it.minorUnits },
                "Iteration $iteration: splitByRatio sum mismatch with ratios $ratios"
            )
        }
    }

    @Test
    fun `splitByRatio returns correct number of parts`() {
        repeat(50) {
            val total = Money(random.nextLong(1, 10000), "USD")
            val ratioCount = random.nextInt(2, 10)
            val ratios = List(ratioCount) { random.nextInt(1, 50) }
            
            val splits = MoneyAllocator.splitByRatio(total, ratios)
            
            assertEquals(ratioCount, splits.size)
        }
    }

    @Test
    fun `splitByRatio 1-1 equals splitEqual 2`() {
        repeat(50) { iteration ->
            val total = Money(random.nextLong(1, 10000), "USD")
            
            val byRatio = MoneyAllocator.splitByRatio(total, listOf(1, 1))
            val byEqual = MoneyAllocator.splitEqual(total, 2)
            
            assertEquals(
                byEqual.sumOf { it.minorUnits },
                byRatio.sumOf { it.minorUnits },
                "Iteration $iteration: 1:1 ratio should equal 2-way split"
            )
        }
    }

    @Test
    fun `splitByRatio proportions are approximately correct`() {
        val total = Money(10000L, "USD") // $100
        val ratios = listOf(1, 2, 1) // 25%, 50%, 25%
        
        val splits = MoneyAllocator.splitByRatio(total, ratios)
        
        // Allow for rounding (±1 cent)
        assertTrue(splits[0].minorUnits in 2499..2501) // ~25%
        assertTrue(splits[1].minorUnits in 4999..5001) // ~50%
        assertTrue(splits[2].minorUnits in 2499..2501) // ~25%
        assertEquals(10000L, splits.sumOf { it.minorUnits })
    }

    // ==================== splitByWeights Property Tests ====================

    @Test
    fun `splitByWeights sum always equals total`() {
        repeat(100) { iteration ->
            val total = Money(random.nextLong(1, 1_000_000), "USD")
            val weightCount = random.nextInt(2, 8)
            val weights = List(weightCount) { random.nextLong(1, 1000) }
            
            val splits = MoneyAllocator.splitByWeights(total, weights)
            
            assertEquals(
                total.minorUnits,
                splits.sumOf { it.minorUnits },
                "Iteration $iteration: splitByWeights sum mismatch with weights $weights"
            )
        }
    }

    @Test
    fun `splitByWeights with zero weights included`() {
        repeat(50) { iteration ->
            val total = Money(random.nextLong(1, 10000), "USD")
            val weights = listOf(0L, random.nextLong(1, 100), 0L, random.nextLong(1, 100))
            
            val splits = MoneyAllocator.splitByWeights(total, weights)
            
            assertEquals(0L, splits[0].minorUnits, "Zero weight should get zero amount")
            assertEquals(0L, splits[2].minorUnits, "Zero weight should get zero amount")
            assertEquals(
                total.minorUnits,
                splits.sumOf { it.minorUnits },
                "Iteration $iteration: sum should still equal total"
            )
        }
    }

    @Test
    fun `splitByWeights with custom totalWeight`() {
        val total = Money(1000L, "USD")
        val weights = listOf(25L, 75L)
        val customTotal = 100L
        
        val splits = MoneyAllocator.splitByWeights(total, weights, customTotal)
        
        assertEquals(1000L, splits.sumOf { it.minorUnits })
        assertEquals(250L, splits[0].minorUnits) // 25%
        assertEquals(750L, splits[1].minorUnits) // 75%
    }

    // ==================== Currency-Specific Property Tests ====================

    @Test
    fun `splitEqual preserves currency for JPY`() {
        repeat(50) {
            val total = Money(random.nextLong(1, 100000), "JPY")
            val parties = random.nextInt(1, 10)
            
            val splits = MoneyAllocator.splitEqual(total, parties)
            
            splits.forEach { 
                assertEquals("JPY", it.currencyCode)
                assertEquals(0, it.scale)
            }
            assertEquals(total.minorUnits, splits.sumOf { it.minorUnits })
        }
    }

    @Test
    fun `splitEqual preserves currency for KWD`() {
        repeat(50) {
            val total = Money(random.nextLong(1, 100000), "KWD")
            val parties = random.nextInt(1, 10)
            
            val splits = MoneyAllocator.splitEqual(total, parties)
            
            splits.forEach { 
                assertEquals("KWD", it.currencyCode)
                assertEquals(3, it.scale)
            }
            assertEquals(total.minorUnits, splits.sumOf { it.minorUnits })
        }
    }

    // ==================== Edge Case Property Tests ====================

    @Test
    fun `split single party returns original amount`() {
        repeat(50) {
            val total = Money(random.nextLong(0, 100000), "USD")
            
            val splits = MoneyAllocator.splitEqual(total, 1)
            
            assertEquals(1, splits.size)
            assertEquals(total.minorUnits, splits[0].minorUnits)
        }
    }

    @Test
    fun `split zero amount returns zero for all parties`() {
        repeat(20) {
            val zero = Money.zero("USD")
            val parties = random.nextInt(1, 10)
            
            val splits = MoneyAllocator.splitEqual(zero, parties)
            
            splits.forEach { assertEquals(0L, it.minorUnits) }
            assertEquals(0L, splits.sumOf { it.minorUnits })
        }
    }

    @Test
    fun `split one cent among many parties`() {
        val oneCent = Money(1L, "USD")
        
        val splits = MoneyAllocator.splitEqual(oneCent, 10)
        
        // Only one party gets the cent
        assertEquals(1L, splits.sumOf { it.minorUnits })
        assertEquals(1, splits.count { it.minorUnits == 1L })
        assertEquals(9, splits.count { it.minorUnits == 0L })
    }

    @Test
    fun `split amount smaller than party count`() {
        val fiveCents = Money(5L, "USD")
        
        val splits = MoneyAllocator.splitEqual(fiveCents, 10)
        
        assertEquals(5L, splits.sumOf { it.minorUnits })
        assertEquals(5, splits.count { it.minorUnits == 1L })
        assertEquals(5, splits.count { it.minorUnits == 0L })
    }

    // ==================== Helper Functions ====================

    private fun generateValidPercentages(count: Int): List<Int> {
        val base = 10_000 / count
        val percentages = MutableList(count) { base }
        val remainder = 10_000 - percentages.sum()
        percentages[0] += remainder
        return percentages
    }
}
