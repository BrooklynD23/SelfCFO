package com.ledgerlens.categorization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExplanationGeneratorTest {

    private val generator = ExplanationGenerator()

    @Test
    fun `generate returns unknown for empty factors`() {
        val result = generator.generate(emptyList(), "test_classifier")

        assertEquals(ExplanationReason.DEFAULT_FALLBACK, result.primaryReason)
        assertEquals(0.0f, result.overallConfidence)
        assertEquals("test_classifier", result.classifierSource)
        assertTrue(result.factors.factors.isEmpty())
    }

    @Test
    fun `generate creates explanation from single factor`() {
        val factors = listOf(
            ExplanationFactor.merchantMatch("Starbucks", 0.9f)
        )

        val result = generator.generate(factors, "merchant_classifier")

        assertEquals(ExplanationReason.MERCHANT_MATCH, result.primaryReason)
        assertEquals(1, result.factors.factors.size)
        assertEquals("merchant_classifier", result.classifierSource)
        assertNotNull(result.humanReadableSummary)
    }

    @Test
    fun `generate handles multiple factors with combined reason`() {
        val factors = listOf(
            ExplanationFactor.merchantMatch("Coffee Shop", 0.4f),
            ExplanationFactor.keywordMatch("coffee", 0.35f),
            ExplanationFactor.amountPattern("small", 0.25f)
        )

        val result = generator.generate(factors, "combined_classifier")

        assertEquals(ExplanationReason.COMBINED_FACTORS, result.primaryReason)
        assertEquals(3, result.factors.factors.size)
        assertTrue(result.hasMultipleFactors)
    }

    @Test
    fun `generate uses primary reason when dominant factor exists`() {
        val factors = listOf(
            ExplanationFactor.merchantMatch("Amazon", 0.8f),
            ExplanationFactor.keywordMatch("shipping", 0.2f)
        )

        val result = generator.generate(factors, "test")

        assertEquals(ExplanationReason.MERCHANT_MATCH, result.primaryReason)
    }

    @Test
    fun `forMerchantMatch creates correct explanation`() {
        val result = generator.forMerchantMatch(
            merchantName = "Walmart",
            confidence = 0.95f,
            matchType = MerchantMatchType.EXACT
        )

        assertEquals(ExplanationReason.MERCHANT_MATCH, result.primaryReason)
        assertEquals(0.95f, result.overallConfidence)
        assertTrue(result.isHighConfidence)
        assertFalse(result.needsReview)
        assertTrue(result.humanReadableSummary?.contains("Walmart") == true)
    }

    @Test
    fun `forMerchantMatch with fuzzy match`() {
        val result = generator.forMerchantMatch(
            merchantName = "Target",
            confidence = 0.75f,
            matchType = MerchantMatchType.FUZZY
        )

        assertEquals(ExplanationReason.MERCHANT_MATCH, result.primaryReason)
        assertEquals(0.75f, result.overallConfidence)
        assertFalse(result.isHighConfidence)
        assertTrue(result.humanReadableSummary?.contains("similar") == true)
    }

    @Test
    fun `forKeywordMatches creates correct explanation`() {
        val keywords = listOf(
            KeywordMatch("coffee", 0.6f, position = 0),
            KeywordMatch("latte", 0.4f, position = 2)
        )

        val result = generator.forKeywordMatches(keywords, 0.8f)

        assertEquals(ExplanationReason.KEYWORD_MATCH, result.primaryReason)
        assertEquals(0.8f, result.overallConfidence)
        assertEquals(2, result.factors.factors.size)
        assertTrue(result.humanReadableSummary?.contains("coffee") == true)
    }

    @Test
    fun `forKeywordMatches returns unknown for empty keywords`() {
        val result = generator.forKeywordMatches(emptyList(), 0.5f)

        assertEquals(ExplanationReason.DEFAULT_FALLBACK, result.primaryReason)
        assertEquals(0.0f, result.overallConfidence)
    }

    @Test
    fun `forUserHistory creates correct explanation`() {
        val result = generator.forUserHistory(
            pattern = "Similar to previous groceries",
            occurrences = 15,
            confidence = 0.88f
        )

        assertEquals(ExplanationReason.USER_HISTORY, result.primaryReason)
        assertEquals(0.88f, result.overallConfidence)
        assertTrue(result.humanReadableSummary?.contains("15") == true)
    }

    @Test
    fun `forRuleMatch creates high confidence explanation`() {
        val result = generator.forRuleMatch(
            ruleId = "rule_001",
            ruleName = "Monthly Netflix"
        )

        assertEquals(ExplanationReason.RULE_MATCH, result.primaryReason)
        assertEquals(1.0f, result.overallConfidence)
        assertTrue(result.isHighConfidence)
        assertTrue(result.humanReadableSummary?.contains("Monthly Netflix") == true)
    }

    @Test
    fun `forCombinedFactors merges multiple factor types`() {
        val merchantFactor = ExplanationFactor.merchantMatch("Grocery Store", 0.5f)
        val keywordFactors = listOf(
            ExplanationFactor.keywordMatch("organic", 0.2f),
            ExplanationFactor.keywordMatch("produce", 0.15f)
        )
        val amountFactor = ExplanationFactor.amountPattern("medium", 0.15f)

        val result = generator.forCombinedFactors(
            merchantFactor = merchantFactor,
            keywordFactors = keywordFactors,
            amountFactor = amountFactor,
            confidence = 0.85f
        )

        assertEquals(ExplanationReason.COMBINED_FACTORS, result.primaryReason)
        assertEquals(4, result.factors.factors.size)
        assertEquals(0.85f, result.overallConfidence)
    }

    @Test
    fun `forCombinedFactors returns unknown when all factors null or empty`() {
        val result = generator.forCombinedFactors(
            merchantFactor = null,
            keywordFactors = emptyList(),
            amountFactor = null,
            historyFactor = null,
            confidence = 0.5f
        )

        assertEquals(ExplanationReason.DEFAULT_FALLBACK, result.primaryReason)
    }

    @Test
    fun `topFactors returns correct number of factors`() {
        val factors = listOf(
            ExplanationFactor.merchantMatch("Store", 0.5f),
            ExplanationFactor.keywordMatch("word1", 0.2f),
            ExplanationFactor.keywordMatch("word2", 0.15f),
            ExplanationFactor.amountPattern("small", 0.15f)
        )

        val result = generator.generate(factors, "test")
        val top2 = result.topFactors(2)

        assertEquals(2, top2.size)
        assertEquals("Store", top2[0].value)
    }

    @Test
    fun `confidence levels are correctly assigned`() {
        val highConf = generator.forMerchantMatch("Test", 0.96f)
        assertEquals(ConfidenceLevel.VERY_HIGH, highConf.confidenceLevel)

        val medConf = generator.forMerchantMatch("Test", 0.70f)
        assertEquals(ConfidenceLevel.MEDIUM, medConf.confidenceLevel)

        val lowConf = generator.forMerchantMatch("Test", 0.45f)
        assertEquals(ConfidenceLevel.VERY_LOW, lowConf.confidenceLevel)
    }
}
