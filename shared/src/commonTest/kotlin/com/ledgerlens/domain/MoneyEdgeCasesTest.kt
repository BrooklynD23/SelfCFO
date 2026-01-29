package com.ledgerlens.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Edge case tests for Money type.
 *
 * Tests focus on:
 * - Zero amounts
 * - Negative amounts
 * - Overflow prevention
 * - Different currency scales (JPY=0, USD=2, KWD=3)
 * - Rounding behavior
 * - Boundary values
 */
class MoneyEdgeCasesTest {

    // ==================== Zero Amount Tests ====================

    @Test
    fun `zero USD is correctly represented`() {
        val zero = Money.zero("USD")
        assertEquals(0L, zero.minorUnits)
        assertTrue(zero.isZero)
        assertFalse(zero.isPositive)
        assertFalse(zero.isNegative)
        assertEquals("0.00", zero.toMajorString())
    }

    @Test
    fun `zero JPY is correctly represented`() {
        val zero = Money.zero("JPY")
        assertEquals(0L, zero.minorUnits)
        assertEquals(0, zero.scale)
        assertEquals("0", zero.toMajorString())
    }

    @Test
    fun `zero KWD is correctly represented`() {
        val zero = Money.zero("KWD")
        assertEquals(0L, zero.minorUnits)
        assertEquals(3, zero.scale)
        assertEquals("0.000", zero.toMajorString())
    }

    @Test
    fun `adding zero returns same value`() {
        val money = Money(1234L, "USD")
        val result = money + Money.zero("USD")
        assertEquals(money.minorUnits, result.minorUnits)
    }

    @Test
    fun `subtracting zero returns same value`() {
        val money = Money(1234L, "USD")
        val result = money - Money.zero("USD")
        assertEquals(money.minorUnits, result.minorUnits)
    }

    @Test
    fun `multiplying by zero returns zero`() {
        val money = Money(1234L, "USD")
        val result = money * 0
        assertEquals(0L, result.minorUnits)
        assertTrue(result.isZero)
    }

    @Test
    fun `zero basis points returns zero`() {
        val money = Money(10000L, "USD")
        val result = money.multiplyByBasisPoints(0)
        assertEquals(0L, result.minorUnits)
    }

    // ==================== Negative Amount Tests ====================

    @Test
    fun `negative amount formatting`() {
        val negative = Money(-1234L, "USD")
        assertEquals("-12.34", negative.toMajorString())
        assertTrue(negative.isNegative)
        assertFalse(negative.isPositive)
    }

    @Test
    fun `negative JPY formatting`() {
        val negative = Money(-1234L, "JPY")
        assertEquals("-1234", negative.toMajorString())
    }

    @Test
    fun `negative KWD formatting`() {
        val negative = Money(-12345L, "KWD")
        assertEquals("-12.345", negative.toMajorString())
    }

    @Test
    fun `negation of negative becomes positive`() {
        val negative = Money(-100L, "USD")
        val positive = -negative
        assertEquals(100L, positive.minorUnits)
        assertTrue(positive.isPositive)
    }

    @Test
    fun `abs of negative returns positive`() {
        val negative = Money(-9999L, "USD")
        val result = negative.abs()
        assertEquals(9999L, result.minorUnits)
        assertTrue(result.isPositive)
    }

    @Test
    fun `subtraction resulting in negative`() {
        val a = Money(100L, "USD")
        val b = Money(300L, "USD")
        val result = a - b
        assertEquals(-200L, result.minorUnits)
        assertTrue(result.isNegative)
    }

    @Test
    fun `parsing negative with parentheses`() {
        val money = Money.parseMajor("(99.99)", "USD")
        assertEquals(-9999L, money.minorUnits)
        assertTrue(money.isNegative)
    }

    @Test
    fun `parsing negative with leading minus`() {
        val money = Money.parseMajor("-99.99", "USD")
        assertEquals(-9999L, money.minorUnits)
    }

    // ==================== Overflow Prevention Tests ====================

    @Test
    fun `large positive values near Long MAX_VALUE`() {
        val large = Money(Long.MAX_VALUE - 100, "USD")
        assertEquals(Long.MAX_VALUE - 100, large.minorUnits)
        assertTrue(large.isPositive)
    }

    @Test
    fun `large negative values near Long MIN_VALUE`() {
        val large = Money(Long.MIN_VALUE + 100, "USD")
        assertEquals(Long.MIN_VALUE + 100, large.minorUnits)
        assertTrue(large.isNegative)
    }

    @Test
    fun `multiplication with large multiplier`() {
        val money = Money(1L, "USD")
        val result = money * Long.MAX_VALUE
        assertEquals(Long.MAX_VALUE, result.minorUnits)
    }

    @Test
    fun `division prevents loss of sign`() {
        val negative = Money(-100L, "USD")
        val result = negative / 3
        assertEquals(-33L, result.minorUnits)
        assertTrue(result.isNegative)
    }

    @Test
    fun `divideWithRemainder preserves total`() {
        val money = Money(100L, "USD")
        val (quotient, remainder) = money.divideWithRemainder(3)
        assertEquals(money.minorUnits, quotient.minorUnits * 3 + remainder.minorUnits)
    }

    // ==================== Currency Scale Tests ====================

    @Test
    fun `JPY zero decimal parsing`() {
        val yen = Money.parseMajor("12345", "JPY")
        assertEquals(12345L, yen.minorUnits)
        assertEquals(0, yen.scale)
    }

    @Test
    fun `JPY with decimal input rounds correctly`() {
        // JPY has 0 decimals, so 123.45 should round to 123
        val yen = Money.parseMajor("123.5", "JPY", RoundingMode.HALF_UP)
        assertEquals(124L, yen.minorUnits)
    }

    @Test
    fun `KWD three decimal parsing`() {
        val dinar = Money.parseMajor("1.234", "KWD")
        assertEquals(1234L, dinar.minorUnits)
        assertEquals(3, dinar.scale)
    }

    @Test
    fun `KWD extra precision rounds`() {
        val dinar = Money.parseMajor("1.2345", "KWD", RoundingMode.HALF_UP)
        assertEquals(1235L, dinar.minorUnits)
    }

    @Test
    fun `USD standard two decimal parsing`() {
        val dollars = Money.parseMajor("123.45", "USD")
        assertEquals(12345L, dollars.minorUnits)
        assertEquals(2, dollars.scale)
    }

    @Test
    fun `BTC eight decimal parsing`() {
        val btc = Money.parseMajor("0.00000001", "BTC")
        assertEquals(1L, btc.minorUnits)
        assertEquals(8, btc.scale)
    }

    @Test
    fun `BTC larger amount`() {
        val btc = Money.parseMajor("1.23456789", "BTC")
        assertEquals(123456789L, btc.minorUnits)
    }

    @Test
    fun `arithmetic preserves currency and scale`() {
        val a = Money(1000L, "KWD")
        val b = Money(234L, "KWD")
        val result = a + b
        assertEquals("KWD", result.currencyCode)
        assertEquals(3, result.scale)
    }

    // ==================== Rounding Behavior Tests ====================

    @Test
    fun `HALF_UP rounds 5 up`() {
        val result = Money.parseMajor("1.235", "USD", RoundingMode.HALF_UP)
        assertEquals(124L, result.minorUnits) // 1.24
    }

    @Test
    fun `HALF_DOWN rounds 5 down`() {
        val result = Money.parseMajor("1.235", "USD", RoundingMode.HALF_DOWN)
        assertEquals(123L, result.minorUnits) // 1.23
    }

    @Test
    fun `HALF_EVEN rounds to even (banker's rounding)`() {
        // 1.235 -> 1.24 (round up to even)
        val up = Money.parseMajor("1.235", "USD", RoundingMode.HALF_EVEN)
        assertEquals(124L, up.minorUnits)

        // 1.225 -> 1.22 (round down to even)
        val down = Money.parseMajor("1.225", "USD", RoundingMode.HALF_EVEN)
        assertEquals(122L, down.minorUnits)
    }

    @Test
    fun `DOWN truncates toward zero`() {
        val positive = Money.parseMajor("1.239", "USD", RoundingMode.DOWN)
        assertEquals(123L, positive.minorUnits)
    }

    @Test
    fun `UP rounds away from zero`() {
        val positive = Money.parseMajor("1.231", "USD", RoundingMode.UP)
        assertEquals(124L, positive.minorUnits)
    }

    @Test
    fun `basis points rounding HALF_UP`() {
        val money = Money(1000L, "USD") // $10.00
        // 8.5% = 850 bps, result = 850/10000 * 1000 = 85
        val result = money.multiplyByBasisPoints(850, RoundingMode.HALF_UP)
        assertEquals(85L, result.minorUnits)
    }

    @Test
    fun `basis points with rounding needed`() {
        val money = Money(333L, "USD") // $3.33
        // 33.33% = 3333 bps
        // 333 * 3333 / 10000 = 1109.889 -> rounds to 111
        val result = money.multiplyByBasisPoints(3333, RoundingMode.HALF_UP)
        assertEquals(111L, result.minorUnits)
    }

    // ==================== Boundary Value Tests ====================

    @Test
    fun `smallest positive amount`() {
        val smallest = Money(1L, "USD")
        assertEquals("0.01", smallest.toMajorString())
        assertTrue(smallest.isPositive)
    }

    @Test
    fun `smallest positive KWD`() {
        val smallest = Money(1L, "KWD")
        assertEquals("0.001", smallest.toMajorString())
    }

    @Test
    fun `one cent operations`() {
        val cent = Money(1L, "USD")
        val twoCents = cent + cent
        assertEquals(2L, twoCents.minorUnits)
        assertEquals("0.02", twoCents.toMajorString())
    }

    @Test
    fun `comparison with same amount`() {
        val a = Money(100L, "USD")
        val b = Money(100L, "USD")
        assertEquals(0, a.compareTo(b))
        assertTrue(a <= b)
        assertTrue(a >= b)
    }

    @Test
    fun `comparison with different amounts`() {
        val smaller = Money(99L, "USD")
        val larger = Money(100L, "USD")
        assertTrue(smaller < larger)
        assertTrue(larger > smaller)
    }

    // ==================== Invalid Input Tests ====================

    @Test
    fun `invalid currency code length throws`() {
        assertFailsWith<IllegalArgumentException> {
            Money(100L, "US") // Too short
        }
    }

    @Test
    fun `long currency code throws`() {
        assertFailsWith<IllegalArgumentException> {
            Money(100L, "USDD") // Too long
        }
    }

    @Test
    fun `negative scale throws`() {
        assertFailsWith<IllegalArgumentException> {
            Money(100L, "USD", -1)
        }
    }

    @Test
    fun `different currencies cannot be compared`() {
        val usd = Money(100L, "USD")
        val eur = Money(100L, "EUR")
        assertFailsWith<IllegalArgumentException> {
            usd.compareTo(eur)
        }
    }

    @Test
    fun `different currencies cannot be added`() {
        val usd = Money(100L, "USD")
        val jpy = Money(100L, "JPY")
        assertFailsWith<IllegalArgumentException> {
            usd + jpy
        }
    }

    @Test
    fun `different currencies cannot be subtracted`() {
        val usd = Money(100L, "USD")
        val kwd = Money(100L, "KWD")
        assertFailsWith<IllegalArgumentException> {
            usd - kwd
        }
    }

    // ==================== Formatting Edge Cases ====================

    @Test
    fun `formatting preserves trailing zeros`() {
        val money = Money(100L, "USD") // $1.00
        assertEquals("1.00", money.toMajorString())
    }

    @Test
    fun `formatting very small KWD`() {
        val money = Money(1L, "KWD")
        assertEquals("0.001", money.toMajorString())
    }

    @Test
    fun `formatting large amounts`() {
        val money = Money(999999999999L, "USD")
        assertEquals("9999999999.99", money.toMajorString())
    }
}
