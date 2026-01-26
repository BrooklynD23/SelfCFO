package com.ledgerlens.categorization.rules

import com.ledgerlens.categorization.AmountBucket
import com.ledgerlens.categorization.TransactionFeatures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuleSuggesterTest {
    private fun createFeatures(
        merchant: String = "Test Merchant",
        description: String = "Test description",
        amountCents: Long = -1000
    ) = TransactionFeatures(
        merchant, description,
        description.lowercase().split(" ").filter {
            it.length >= 2
        },
        amountCents, AmountBucket.fromCents(amountCents), amountCents < 0, 0, 15, null
    )
    private fun createCorrection(txId: String, features: TransactionFeatures, newCategory: String) =
        UserCorrection(txId, features, "uncategorized", newCategory)

    @Test fun generateSuggestions_noSuggestionsWithFewCorrections() {
        val suggester = RuleSuggester()
        suggester.recordCorrection(createCorrection("tx-1", createFeatures(merchant = "STARBUCKS"), "dining"))
        suggester.recordCorrection(createCorrection("tx-2", createFeatures(merchant = "STARBUCKS"), "dining"))
        assertTrue(suggester.generateSuggestions().isEmpty())
    }

    @Test fun generateSuggestions_suggestsMerchantRule() {
        val suggester = RuleSuggester()
        repeat(4) { i -> suggester.recordCorrection(createCorrection("tx-$i", createFeatures(merchant = "STARBUCKS STORE #${1000 + i}"), "dining")) }
        val suggestions = suggester.generateSuggestions()
        assertTrue(suggestions.isNotEmpty())
        val merchantSuggestion = suggestions.find { it.rule.conditions.conditions.any { c -> c is MerchantContains } }
        assertTrue(merchantSuggestion != null)
        assertEquals("dining", merchantSuggestion.rule.categoryId)
    }

    @Test fun generateSuggestions_separatesSuggestionsByCategory() {
        val suggester = RuleSuggester()
        repeat(3) { i -> suggester.recordCorrection(createCorrection("dining-$i", createFeatures(merchant = "RESTAURANT $i"), "dining")) }
        repeat(3) { i -> suggester.recordCorrection(createCorrection("grocery-$i", createFeatures(merchant = "GROCERY STORE $i"), "groceries")) }
        val suggestions = suggester.generateSuggestions()
        assertTrue(suggestions.any { it.rule.categoryId == "dining" })
        assertTrue(suggestions.any { it.rule.categoryId == "groceries" })
    }

    @Test fun correctionCount_tracksCorrectly() {
        val suggester = RuleSuggester()
        assertEquals(0, suggester.correctionCount())
        suggester.recordCorrection(createCorrection("tx-1", createFeatures(), "dining"))
        assertEquals(1, suggester.correctionCount())
        suggester.recordCorrection(createCorrection("tx-2", createFeatures(), "groceries"))
        assertEquals(2, suggester.correctionCount())
    }

    @Test fun clearCorrections_removesAll() {
        val suggester = RuleSuggester()
        repeat(5) { i -> suggester.recordCorrection(createCorrection("tx-$i", createFeatures(), "dining")) }
        assertEquals(5, suggester.correctionCount())
        suggester.clearCorrections()
        assertEquals(0, suggester.correctionCount())
        assertTrue(suggester.generateSuggestions().isEmpty())
    }

    @Test fun recordCorrection_trimsOldCorrections() {
        val suggester = RuleSuggester(SuggesterConfig(maxCorrectionsToTrack = 5))
        repeat(10) { i -> suggester.recordCorrection(createCorrection("tx-$i", createFeatures(), "dining")) }
        assertEquals(5, suggester.correctionCount())
    }

    @Test fun suggestion_hasCorrectSource() {
        val suggester = RuleSuggester()
        repeat(4) { i -> suggester.recordCorrection(createCorrection("tx-$i", createFeatures(merchant = "STARBUCKS"), "dining")) }
        suggester.generateSuggestions().forEach { assertEquals(RuleSource.SUGGESTED, it.rule.source) }
    }

    @Test fun suggestion_hasReasonDescription() {
        val suggester = RuleSuggester()
        repeat(4) { i -> suggester.recordCorrection(createCorrection("tx-$i", createFeatures(merchant = "STARBUCKS STORE"), "dining")) }
        suggester.generateSuggestions().forEach { assertTrue(it.reason.isNotBlank()) }
    }

    @Test fun suggestion_isHighConfidenceProperty() {
        val suggester = RuleSuggester()
        repeat(10) { i -> suggester.recordCorrection(createCorrection("tx-$i", createFeatures(merchant = "STARBUCKS COFFEE"), "dining")) }
        suggester.generateSuggestions().forEach { assertEquals(it.confidence >= 0.8f, it.isHighConfidence) }
    }
}
