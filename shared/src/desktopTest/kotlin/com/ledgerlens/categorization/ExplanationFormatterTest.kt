package com.ledgerlens.categorization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExplanationFormatterTest {

    private val formatter = ExplanationFormatter()
    private val generator = ExplanationGenerator()

    @Test
    fun `formatSummary returns human readable summary when available`() {
        val explanation = CategoryExplanation(
            primaryReason = ExplanationReason.MERCHANT_MATCH,
            factors = FactorCollection.of(
                ExplanationFactor.merchantMatch("Starbucks", 1.0f)
            ),
            overallConfidence = 0.9f,
            classifierSource = "test",
            humanReadableSummary = "Custom summary text"
        )

        val result = formatter.formatSummary(explanation)

        assertEquals("Custom summary text", result)
    }

    @Test
    fun `formatSummary generates default when no summary provided`() {
        val explanation = CategoryExplanation(
            primaryReason = ExplanationReason.MERCHANT_MATCH,
            factors = FactorCollection.of(
                ExplanationFactor.merchantMatch("Amazon", 1.0f)
            ),
            overallConfidence = 0.9f,
            classifierSource = "test"
        )

        val result = formatter.formatSummary(explanation)

        assertTrue(result.contains("Amazon"))
    }

    @Test
    fun `formatConfidence shows level and percentage`() {
        val explanation = generator.forMerchantMatch("Test", 0.87f)

        val result = formatter.formatConfidence(explanation)

        assertTrue(result.contains("87%"))
        assertTrue(result.contains("Confident") || result.contains("High"))
    }

    @Test
    fun `formatConfidence handles very high confidence`() {
        val explanation = generator.forMerchantMatch("Test", 0.98f)

        val result = formatter.formatConfidence(explanation)

        assertTrue(result.contains("98%"))
        assertTrue(result.contains("Very"))
    }

    @Test
    fun `formatConfidence handles low confidence`() {
        val explanation = generator.forMerchantMatch("Test", 0.35f)

        val result = formatter.formatConfidence(explanation)

        assertTrue(result.contains("35%"))
    }

    @Test
    fun `formatDetailed returns structured explanation`() {
        val explanation = generator.forMerchantMatch("Costco", 0.92f)

        val result = formatter.formatDetailed(explanation)

        assertNotNull(result.summary)
        assertNotNull(result.confidence)
        assertTrue(result.factors.isNotEmpty())
        assertEquals("merchant_classifier", result.classifier)
    }

    @Test
    fun `formatDetailed multiLine output is properly formatted`() {
        val explanation = generator.forKeywordMatches(
            listOf(
                KeywordMatch("grocery", 0.6f),
                KeywordMatch("food", 0.4f)
            ),
            0.8f
        )

        val result = formatter.formatDetailed(explanation)
        val multiLine = result.toMultiLine()

        assertTrue(multiLine.contains("Confidence:"))
        assertTrue(multiLine.contains("Contributing factors:"))
    }

    @Test
    fun `formatFactors orders by weight descending`() {
        val factors = FactorCollection.of(
            ExplanationFactor.keywordMatch("low", 0.1f),
            ExplanationFactor.keywordMatch("high", 0.6f),
            ExplanationFactor.keywordMatch("medium", 0.3f)
        )

        val result = formatter.formatFactors(factors)

        assertEquals(3, result.size)
        assertEquals("high", result[0].description.substringAfter("'").substringBefore("'"))
        assertEquals(60, result[0].percentage)
    }

    @Test
    fun `formatFactor handles merchant exact match`() {
        val factor = ExplanationFactor.merchantMatch("Target", 0.9f, "exact")

        val result = formatter.formatFactor(factor)

        assertTrue(result.contains("Merchant"))
        assertTrue(result.contains("Target"))
        assertFalse(result.contains("normalized"))
    }

    @Test
    fun `formatFactor handles merchant fuzzy match`() {
        val factor = ExplanationFactor.merchantMatch("Targett", 0.7f, "fuzzy")

        val result = formatter.formatFactor(factor)

        assertTrue(result.contains("similar"))
        assertTrue(result.contains("Targett"))
    }

    @Test
    fun `formatFactor handles keyword match`() {
        val factor = ExplanationFactor.keywordMatch("coffee", 0.5f)

        val result = formatter.formatFactor(factor)

        assertTrue(result.contains("Keyword"))
        assertTrue(result.contains("coffee"))
    }

    @Test
    fun `formatFactor handles amount pattern with range`() {
        val factor = ExplanationFactor.amountPattern(
            pattern = "medium",
            weight = 0.3f,
            amountRange = "$25-$100"
        )

        val result = formatter.formatFactor(factor)

        assertTrue(result.contains("Amount"))
        assertTrue(result.contains("medium"))
        assertTrue(result.contains("$25-$100"))
    }

    @Test
    fun `formatFactor handles user history with occurrences`() {
        val factor = ExplanationFactor.userHistory(
            description = "similar groceries",
            weight = 0.4f,
            occurrences = 12
        )

        val result = formatter.formatFactor(factor)

        assertTrue(result.contains("history"))
        assertTrue(result.contains("12"))
    }

    @Test
    fun `formatFactor handles rule match`() {
        val factor = ExplanationFactor.ruleMatch("r001", "Subscription Rule")

        val result = formatter.formatFactor(factor)

        assertTrue(result.contains("Rule"))
        assertTrue(result.contains("Subscription Rule"))
    }

    @Test
    fun `formatAccessible creates screen reader friendly output`() {
        val explanation = generator.forMerchantMatch("Walmart", 0.9f)

        val result = formatter.formatAccessible(explanation)

        assertTrue(result.contains("Category explanation"))
        assertTrue(result.contains("Confidence"))
        assertTrue(result.contains("percent"))
    }

    @Test
    fun `formatCompact returns brief inline string`() {
        val explanation = generator.forMerchantMatch("Netflix", 0.95f)

        val result = formatter.formatCompact(explanation)

        assertEquals("Netflix (95%)", result)
    }

    @Test
    fun `formatCompact handles unknown explanation`() {
        val explanation = CategoryExplanation.unknown()

        val result = formatter.formatCompact(explanation)

        assertEquals("Unknown", result)
    }

    @Test
    fun `FormattedFactor indicates significance correctly`() {
        val factors = FactorCollection.of(
            ExplanationFactor.keywordMatch("major", 0.8f),
            ExplanationFactor.keywordMatch("minor", 0.05f)
        )

        val result = formatter.formatFactors(factors)

        assertTrue(result[0].isSignificant)
        assertFalse(result[1].isSignificant)
    }

    @Test
    fun `custom string provider is used for localization`() {
        val customProvider = object : LocalizedStringProvider {
            override fun getString(key: String): String? = when (key) {
                "confidence.high" -> "Très confiant"
                else -> null
            }

            override fun getString(key: String, vararg args: Any): String? = getString(key)
        }

        val customFormatter = ExplanationFormatter(customProvider)
        val explanation = generator.forMerchantMatch("Test", 0.87f)

        val result = customFormatter.formatConfidence(explanation)

        assertTrue(result.contains("Très confiant"))
    }

    @Test
    fun `DefaultStringProvider returns all reason strings`() {
        ExplanationReason.entries.forEach { reason ->
            val result = DefaultStringProvider.getString(reason.localizationKey)
            assertNotNull(result, "Missing string for ${reason.localizationKey}")
        }
    }

    @Test
    fun `DefaultStringProvider returns all confidence level strings`() {
        ConfidenceLevel.entries.forEach { level ->
            val result = DefaultStringProvider.getString(level.localizationKey)
            assertNotNull(result, "Missing string for ${level.localizationKey}")
        }
    }

    @Test
    fun `ExplanationLocalizationKeys contains all required keys`() {
        assertTrue(ExplanationLocalizationKeys.allKeys.isNotEmpty())
        assertTrue(ExplanationLocalizationKeys.allKeys.contains(ExplanationLocalizationKeys.REASON_MERCHANT_MATCH))
        assertTrue(ExplanationLocalizationKeys.allKeys.contains(ExplanationLocalizationKeys.CONFIDENCE_HIGH))
    }
}
