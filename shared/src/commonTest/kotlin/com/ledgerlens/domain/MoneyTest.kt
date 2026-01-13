package com.ledgerlens.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class MoneyTest {

    @Test
    fun `create from major units`() {
        val money = Money.parseMajor("12.34", "USD")
        assertEquals(1234L, money.minorUnits)
        assertEquals("USD", money.currencyCode)
        assertEquals(2, money.scale)
    }

    @Test
    fun `create from minor units`() {
        val money = Money.fromMinorUnits(1234L, "USD")
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
    fun `subtraction works correctly`() {
        val a = Money(1234L, "USD")
        val b = Money(234L, "USD")
        val result = a - b
        assertEquals(1000L, result.minorUnits)
    }

    @Test
    fun `multiplication by int works correctly`() {
        val money = Money(100L, "USD")
        val result = money * 3
        assertEquals(300L, result.minorUnits)
    }

    @Test
    fun `division works correctly`() {
        val money = Money(100L, "USD")
        val result = money / 4
        assertEquals(25L, result.minorUnits)
    }

    @Test
    fun `negation works correctly`() {
        val money = Money(100L, "USD")
        val negated = -money
        assertEquals(-100L, negated.minorUnits)
    }

    @Test
    fun `abs works correctly`() {
        val negative = Money(-100L, "USD")
        val result = negative.abs()
        assertEquals(100L, result.minorUnits)
    }

    @Test
    fun `comparison works correctly`() {
        val a = Money(100L, "USD")
        val b = Money(200L, "USD")
        assertTrue(a < b)
        assertTrue(b > a)
        assertEquals(0, a.compareTo(Money(100L, "USD")))
    }

    @Test
    fun `isPositive isNegative isZero work correctly`() {
        val positive = Money(100L, "USD")
        val negative = Money(-100L, "USD")
        val zero = Money(0L, "USD")

        assertTrue(positive.isPositive)
        assertFalse(positive.isNegative)
        assertFalse(positive.isZero)

        assertFalse(negative.isPositive)
        assertTrue(negative.isNegative)
        assertFalse(negative.isZero)

        assertFalse(zero.isPositive)
        assertFalse(zero.isNegative)
        assertTrue(zero.isZero)
    }

    @Test
    fun `toMajorString formats correctly`() {
        val money = Money(1234L, "USD")
        assertEquals("12.34", money.toMajorString())

        val negative = Money(-1234L, "USD")
        assertEquals("-12.34", negative.toMajorString())
    }

    @Test
    fun `JPY has zero decimal places`() {
        val yen = Money.parseMajor("1234", "JPY")
        assertEquals(1234L, yen.minorUnits)
        assertEquals(0, yen.scale)
        assertEquals("1234", yen.toMajorString())
    }

    @Test
    fun `KWD has three decimal places`() {
        val dinar = Money.parseMajor("12.345", "KWD")
        assertEquals(12345L, dinar.minorUnits)
        assertEquals(3, dinar.scale)
        assertEquals("12.345", dinar.toMajorString())
    }

    @Test
    fun `parsing with thousands separators works`() {
        val money = Money.parseMajor("1,234.56", "USD")
        assertEquals(123456L, money.minorUnits)
    }

    @Test
    fun `parsing negative with parentheses works`() {
        val money = Money.parseMajor("(12.34)", "USD")
        assertEquals(-1234L, money.minorUnits)
    }

    @Test
    fun `parsing negative with minus sign works`() {
        val money = Money.parseMajor("-12.34", "USD")
        assertEquals(-1234L, money.minorUnits)
    }

    @Test
    fun `multiplyByBasisPoints works correctly`() {
        val money = Money(10000L, "USD") // $100.00
        val result = money.multiplyByBasisPoints(850) // 8.5%
        assertEquals(850L, result.minorUnits) // $8.50
    }

    @Test
    fun `different currencies cannot be added`() {
        val usd = Money(100L, "USD")
        val eur = Money(100L, "EUR")
        assertFailsWith<IllegalArgumentException> {
            usd + eur
        }
    }

    @Test
    fun `zero creates zero money`() {
        val zero = Money.zero("USD")
        assertEquals(0L, zero.minorUnits)
        assertEquals("USD", zero.currencyCode)
        assertTrue(zero.isZero)
    }
}

class MoneyAllocatorTest {

    @Test
    fun `split evenly with no remainder`() {
        val total = Money(1000L, "USD")  // $10.00
        val splits = MoneyAllocator.splitEqual(total, 4)

        assertEquals(4, splits.size)
        splits.forEach { assertEquals(250L, it.minorUnits) }
        assertEquals(total.minorUnits, splits.sumOf { it.minorUnits })
    }

    @Test
    fun `split evenly with remainder goes to first`() {
        val total = Money(1000L, "USD")  // $10.00
        val splits = MoneyAllocator.splitEqual(total, 3)

        assertEquals(3, splits.size)
        assertEquals(334L, splits[0].minorUnits)  // Gets remainder
        assertEquals(333L, splits[1].minorUnits)
        assertEquals(333L, splits[2].minorUnits)
        assertEquals(total.minorUnits, splits.sumOf { it.minorUnits })
    }

    @Test
    fun `split evenly with remainder goes to last`() {
        val total = Money(1000L, "USD")
        val splits = MoneyAllocator.splitEqual(
            total, 3,
            remainderRecipient = MoneyAllocator.RemainderRecipient.LAST
        )

        assertEquals(3, splits.size)
        assertEquals(333L, splits[0].minorUnits)
        assertEquals(333L, splits[1].minorUnits)
        assertEquals(334L, splits[2].minorUnits)  // Gets remainder
        assertEquals(total.minorUnits, splits.sumOf { it.minorUnits })
    }

    @Test
    fun `percentage split sums to total`() {
        val total = Money(10000L, "USD")  // $100.00
        val splits = MoneyAllocator.splitByPercentBps(total, listOf(3333, 3333, 3334))

        assertEquals(total.minorUnits, splits.sumOf { it.minorUnits })
    }

    @Test
    fun `ratio split works correctly`() {
        val total = Money(100L, "USD")  // $1.00
        val splits = MoneyAllocator.splitByRatio(total, listOf(1, 2, 1))

        assertEquals(4, splits.size)
        assertEquals(total.minorUnits, splits.sumOf { it.minorUnits })
        // 25%, 50%, 25%
        assertEquals(25L, splits[0].minorUnits)
        assertEquals(50L, splits[1].minorUnits)
        assertEquals(25L, splits[2].minorUnits)
    }

    @Test
    fun `ratio split with remainder`() {
        val total = Money(101L, "USD")
        val splits = MoneyAllocator.splitByRatio(total, listOf(1, 1))

        assertEquals(2, splits.size)
        assertEquals(total.minorUnits, splits.sumOf { it.minorUnits })
    }
}

class MoneyParserTest {

    @Test
    fun `parse simple decimal`() {
        val result = MoneyParser.parseToMinorUnits("12.34", 2)
        assertEquals(1234L, result)
    }

    @Test
    fun `parse whole number`() {
        val result = MoneyParser.parseToMinorUnits("100", 2)
        assertEquals(10000L, result)
    }

    @Test
    fun `parse with extra precision rounds correctly`() {
        val result = MoneyParser.parseToMinorUnits("12.345", 2, RoundingMode.HALF_UP)
        assertEquals(1235L, result) // Rounds up
    }

    @Test
    fun `parse with extra precision rounds down`() {
        val result = MoneyParser.parseToMinorUnits("12.344", 2, RoundingMode.HALF_UP)
        assertEquals(1234L, result) // Rounds down
    }

    @Test
    fun `parse European format`() {
        val result = MoneyParser.parseToMinorUnits("1.234,56", 2)
        assertEquals(123456L, result)
    }
}
