package com.ledgerlens.import

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MerchantNormalizerTest {

    private val normalizer = MerchantNormalizer()

    // === Noise Pattern Removal Tests ===

    @Test
    fun `removes terminal ID numbers`() {
        val result = normalizer.normalize("GROCERY STORE 12345678")
        assertTrue(result.canonical.lowercase().contains("grocery"))
        assertTrue(!result.canonical.contains("12345678"))
    }

    @Test
    fun `removes state codes and ZIP`() {
        val result = normalizer.normalize("TARGET STORE CA 90210")
        assertEquals("Target", result.canonical)
    }

    @Test
    fun `removes store numbers with hash`() {
        val result = normalizer.normalize("WALGREENS #1234")
        assertEquals("Walgreens", result.canonical)
    }

    @Test
    fun `removes masked card numbers`() {
        val result = normalizer.normalize("AMAZON PURCHASE ***1234")
        assertEquals("Amazon", result.canonical)
    }

    @Test
    fun `removes POS and transaction type keywords`() {
        val result = normalizer.normalize("POS DEBIT STARBUCKS PURCHASE")
        assertEquals("Starbucks", result.canonical)
    }

    @Test
    fun `removes date patterns MM-DD`() {
        val result = normalizer.normalize("NETFLIX 03/15 SUBSCRIPTION")
        assertEquals("Netflix", result.canonical)
    }

    @Test
    fun `removes masked account numbers`() {
        val result = normalizer.normalize("TRANSFER FROM XX1234")
        assertTrue(!result.canonical.contains("XX1234"))
    }

    // === Known Alias Mapping Tests ===

    @Test
    fun `maps AMZN to Amazon`() {
        val result = normalizer.normalize("AMZN MKTP US*AB12CD34E")
        assertEquals("Amazon Marketplace", result.canonical)
        assertEquals(MatchType.ALIAS, result.matchType)
    }

    @Test
    fun `maps AMAZON_COM to Amazon`() {
        val result = normalizer.normalize("AMAZON.COM*AB12CD34E")
        assertEquals("Amazon", result.canonical)
        assertEquals(MatchType.ALIAS, result.matchType)
    }

    @Test
    fun `maps UBER TRIP variations`() {
        val result1 = normalizer.normalize("UBER   TRIP 12345")
        assertEquals("Uber", result1.canonical)

        val result2 = normalizer.normalize("UBER TRIP 67890")
        assertEquals("Uber", result2.canonical)
    }

    @Test
    fun `maps UBER EATS`() {
        val result = normalizer.normalize("UBER EATS PENDING")
        assertEquals("Uber Eats", result.canonical)
    }

    @Test
    fun `maps Starbucks variants`() {
        val result = normalizer.normalize("STARBUCKS STORE 12345 CA")
        assertEquals("Starbucks", result.canonical)
    }

    @Test
    fun `maps McDonald's variants`() {
        val result1 = normalizer.normalize("MCDONALD'S F12345")
        assertEquals("McDonald's", result1.canonical)

        val result2 = normalizer.normalize("MCDONALDS 67890")
        assertEquals("McDonald's", result2.canonical)
    }

    @Test
    fun `maps Walmart variants`() {
        val result1 = normalizer.normalize("WAL-MART #1234 STORE")
        assertEquals("Walmart", result1.canonical)

        val result2 = normalizer.normalize("WM SUPERCENTER #5678")
        assertEquals("Walmart", result2.canonical)
    }

    @Test
    fun `maps Square payments`() {
        val result = normalizer.normalize("SQ *COFFEE SHOP")
        assertEquals("Square", result.canonical)
    }

    @Test
    fun `maps Google payments`() {
        val result = normalizer.normalize("GOOGLE *SERVICES")
        assertEquals("Google", result.canonical)
    }

    @Test
    fun `maps PayPal payments`() {
        val result = normalizer.normalize("PAYPAL *MERCHANT NAME")
        assertEquals("PayPal", result.canonical)
    }

    // === Heuristic Extraction Tests ===

    @Test
    fun `extracts merchant name before delimiter dash`() {
        val result = normalizer.normalize("LOCAL CAFE - DOWNTOWN LOCATION")
        assertEquals("Local Cafe", result.canonical)
        assertEquals(MatchType.HEURISTIC, result.matchType)
    }

    @Test
    fun `extracts merchant name before asterisk`() {
        val result = normalizer.normalize("RESTAURANT ABC * ORDER 12345")
        assertEquals("Restaurant Abc", result.canonical)
    }

    @Test
    fun `truncates very long merchant names`() {
        val longName = "A".repeat(100)
        val result = normalizer.normalize(longName)
        assertTrue(result.canonical.length <= 50)
    }

    @Test
    fun `handles empty description`() {
        val result = normalizer.normalize("")
        assertEquals("Unknown", result.canonical)
    }

    @Test
    fun `handles whitespace only description`() {
        val result = normalizer.normalize("   ")
        assertEquals("Unknown", result.canonical)
    }

    @Test
    fun `applies title case to heuristic results`() {
        val result = normalizer.normalize("some random merchant")
        assertEquals("Some Random Merchant", result.canonical)
    }

    @Test
    fun `preserves common acronyms in title case`() {
        val result = normalizer.normalize("ACME LLC")
        assertTrue(result.canonical.contains("LLC"))
    }

    // === Description Prefix Tests ===

    @Test
    fun `generates normalized description prefix`() {
        val prefix = normalizer.normalizedDescriptionPrefix("STARBUCKS STORE #1234 CA 90210")
        assertEquals("starbucks", prefix)
        assertTrue(prefix.length <= 20)
    }

    @Test
    fun `prefix is lowercase and truncated`() {
        val prefix = normalizer.normalizedDescriptionPrefix("VERY LONG MERCHANT NAME THAT EXCEEDS TWENTY CHARACTERS")
        assertEquals(20, prefix.length)
        assertEquals(prefix, prefix.lowercase())
    }

    // === Raw Description Preservation ===

    @Test
    fun `preserves raw description in result`() {
        val raw = "AMZN MKTP US*AB12CD34E"
        val result = normalizer.normalize(raw)
        assertEquals(raw, result.raw)
    }

    // === Edge Cases ===

    @Test
    fun `handles special characters`() {
        val result = normalizer.normalize("MERCHANT & CO. (USA)")
        assertTrue(result.canonical.isNotEmpty())
    }

    @Test
    fun `handles numeric only description`() {
        val result = normalizer.normalize("123456789")
        // After removing long numbers, should have something
        assertTrue(result.canonical.isNotEmpty())
    }

    @Test
    fun `handles mixed case input consistently`() {
        val result1 = normalizer.normalize("Amazon Purchase")
        val result2 = normalizer.normalize("AMAZON PURCHASE")
        val result3 = normalizer.normalize("amazon purchase")
        
        assertEquals(result1.canonical, result2.canonical)
        assertEquals(result2.canonical, result3.canonical)
    }

    @Test
    fun `longest alias match wins`() {
        // APPLE.COM/BILL should win over the more general APPLE.COM alias.
        val result = normalizer.normalize("APPLE.COM/BILL SUBSCRIPTION")
        assertEquals("Apple", result.canonical)
    }
}
