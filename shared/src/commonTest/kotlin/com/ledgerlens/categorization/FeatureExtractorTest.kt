package com.ledgerlens.categorization

import com.ledgerlens.domain.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeatureExtractorTest {

    private val extractor = FeatureExtractor()

    @Test
    fun `tokenize removes punctuation and normalizes case`() {
        val tokens = extractor.tokenize("AMAZON.COM*AMZN MKTP US")
        assertTrue(tokens.contains("amazon"))
        assertTrue(tokens.contains("amzn"))
        assertTrue(tokens.contains("mktp"))
    }

    @Test
    fun `tokenize filters stop words`() {
        val tokens = extractor.tokenize("Purchase at the store for items")
        assertFalse(tokens.contains("the"))
        assertFalse(tokens.contains("at"))
        assertFalse(tokens.contains("for"))
        assertFalse(tokens.contains("purchase"))
        assertTrue(tokens.contains("store"))
        assertTrue(tokens.contains("items"))
    }

    @Test
    fun `tokenize filters short tokens`() {
        val tokens = extractor.tokenize("A B CD EFG HIJK")
        assertFalse(tokens.contains("a"))
        assertFalse(tokens.contains("b"))
        assertTrue(tokens.contains("cd"))
        assertTrue(tokens.contains("efg"))
        assertTrue(tokens.contains("hijk"))
    }

    @Test
    fun `tokenize returns distinct tokens`() {
        val tokens = extractor.tokenize("amazon amazon amazon store")
        assertEquals(1, tokens.count { it == "amazon" })
    }

    @Test
    fun `extract normalizes merchant name`() {
        val features = extractor.extract(
            descriptionRaw = "AMZN MKTP US*AB1234567",
            amount = Money.fromMinorUnits(-2500, "USD")
        )
        assertEquals("Amazon Marketplace", features.merchantNormalized)
    }

    @Test
    fun `extract computes correct amount bucket for micro amounts`() {
        val features = extractor.extract(
            descriptionRaw = "Coffee Shop",
            amount = Money.fromMinorUnits(-350, "USD")
        )
        assertEquals(AmountBucket.MICRO, features.amountBucket)
    }

    @Test
    fun `extract computes correct amount bucket for small amounts`() {
        val features = extractor.extract(
            descriptionRaw = "Lunch",
            amount = Money.fromMinorUnits(-1500, "USD")
        )
        assertEquals(AmountBucket.SMALL, features.amountBucket)
    }

    @Test
    fun `extract computes correct amount bucket for medium amounts`() {
        val features = extractor.extract(
            descriptionRaw = "Grocery Store",
            amount = Money.fromMinorUnits(-7500, "USD")
        )
        assertEquals(AmountBucket.MEDIUM, features.amountBucket)
    }

    @Test
    fun `extract computes correct amount bucket for large amounts`() {
        val features = extractor.extract(
            descriptionRaw = "Electronics Store",
            amount = Money.fromMinorUnits(-25000, "USD")
        )
        assertEquals(AmountBucket.LARGE, features.amountBucket)
    }

    @Test
    fun `extract computes correct amount bucket for very large amounts`() {
        val features = extractor.extract(
            descriptionRaw = "Rent Payment",
            amount = Money.fromMinorUnits(-150000, "USD")
        )
        assertEquals(AmountBucket.VERY_LARGE, features.amountBucket)
    }

    @Test
    fun `extract computes correct amount bucket for huge amounts`() {
        val features = extractor.extract(
            descriptionRaw = "Car Payment",
            amount = Money.fromMinorUnits(-500000, "USD")
        )
        assertEquals(AmountBucket.HUGE, features.amountBucket)
    }

    @Test
    fun `extract identifies debit transactions`() {
        val features = extractor.extract(
            descriptionRaw = "Store Purchase",
            amount = Money.fromMinorUnits(-5000, "USD")
        )
        assertTrue(features.isDebit)
    }

    @Test
    fun `extract identifies credit transactions`() {
        val features = extractor.extract(
            descriptionRaw = "Payroll Deposit",
            amount = Money.fromMinorUnits(250000, "USD")
        )
        assertFalse(features.isDebit)
    }

    @Test
    fun `extract preserves account ID`() {
        val features = extractor.extract(
            descriptionRaw = "Test Transaction",
            amount = Money.fromMinorUnits(-1000, "USD"),
            accountId = "checking-001"
        )
        assertEquals("checking-001", features.accountId)
    }

    @Test
    fun `extractNgrams creates bigrams`() {
        val tokens = listOf("starbucks", "coffee", "purchase")
        val ngrams = extractor.extractNgrams(tokens, 2)
        assertEquals(2, ngrams.size)
        assertTrue(ngrams.contains("starbucks coffee"))
        assertTrue(ngrams.contains("coffee purchase"))
    }

    @Test
    fun `extractNgrams handles insufficient tokens`() {
        val tokens = listOf("single")
        val ngrams = extractor.extractNgrams(tokens, 2)
        assertTrue(ngrams.isEmpty())
    }

    @Test
    fun `amount bucket handles absolute values for credits`() {
        val bucket = AmountBucket.fromCents(5000) // $50 credit
        assertEquals(AmountBucket.MEDIUM, bucket)
    }
}
