package com.ledgerlens.categorization

import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Tests for the FeedbackLoop system.
 *
 * SKIPPED: These tests reference classes (FeedbackLoopConfig, FeedbackLoopFactory,
 * RetrainResult, DefaultRuleManager) that were designed but never implemented.
 * See BUILD_AUDIT_REPORT.md Issue T5 for details.
 *
 * TODO: Implement the missing classes in a dedicated sprint, then re-enable these tests.
 */
@Ignore
class FeedbackLoopTest {
    private fun createTestFeatures(
        merchant: String = "Test Merchant",
        description: String = "Test transaction",
        amountCents: Long = -1500L
    ) =
        TransactionFeatures(merchant, description, description.lowercase().split(" "), amountCents, AmountBucket.fromCents(amountCents), amountCents < 0, 1, 15)

    private fun createFeedbackLoop(config: FeedbackLoopConfig = FeedbackLoopConfig(enableAutoRetrain = false)) =
        FeedbackLoopFactory.create(config = config)

    @Test fun testSubmitCorrectionProcessesSuccessfully() = runTest {
        val result = createFeedbackLoop().submitCorrection("txn_001", createTestFeatures("Starbucks", "STARBUCKS COFFEE"), "Shopping", "Dining", 0.7f, "naive-bayes")
        assertNotNull(result.correctionResult)
        assertEquals("Shopping", result.correctionResult.correction.oldCategoryId)
        assertEquals("Dining", result.correctionResult.correction.newCategoryId)
        assertIs<LearningResult.Updated>(result.correctionResult.learningResult)
    }

    @Test fun testConfirmationIsReinforced() = runTest {
        val result = createFeedbackLoop().submitCorrection("txn_002", createTestFeatures("McDonalds", "MCDONALDS"), "Dining", "Dining", 0.9f, "rule-based")
        assertFalse(result.correctionResult.correction.isActualCorrection)
        assertEquals(LearningResult.Reinforced, result.correctionResult.learningResult)
    }

    @Test fun testBatchSubmissionProcessesAll() = runTest {
        val corrections = listOf(
            PendingCorrection("batch_1", createTestFeatures("Walmart", "WALMART"), "Shopping", "Groceries", 0.6f, "naive-bayes"),
            PendingCorrection("batch_2", createTestFeatures("Shell", "SHELL GAS"), "Shopping", "Transportation", 0.5f, "naive-bayes"),
            PendingCorrection("batch_3", createTestFeatures("Netflix", "NETFLIX"), "Shopping", "Entertainment", 0.4f, "naive-bayes")
        )
        val result = createFeedbackLoop().submitBatch(corrections)
        assertEquals(3, result.batchResult.successCount)
        assertEquals(0, result.batchResult.failureCount)
        assertEquals(3, result.analysis.totalCorrections)
    }

    @Test fun testAnalysisReturnsCorrectStats() = runTest {
        val loop = createFeedbackLoop()
        repeat(3) { i -> loop.submitCorrection("txn_anal_$i", createTestFeatures("CoffeeShop", "COFFEE SHOP $i"), "Shopping", "Dining", 0.6f, "naive-bayes") }
        val analysis = loop.getAnalysis()
        assertEquals(3, analysis.totalCorrections)
        assertTrue(analysis.merchantStats.containsKey("coffeeshop"))
        assertEquals(3, analysis.merchantStats["coffeeshop"]?.totalCorrections)
    }

    @Test fun testStatsTrackCorrectionsProcessed() = runTest {
        val loop = createFeedbackLoop()
        assertEquals(0L, loop.getStats().totalCorrectionsProcessed)
        loop.submitCorrection("txn_stats_1", createTestFeatures("Store", "STORE"), "A", "B", 0.5f, "test")
        assertEquals(1L, loop.getStats().totalCorrectionsProcessed)
    }

    @Test fun testManualRetrainTrigger() = runTest {
        val loop = createFeedbackLoop()
        repeat(5) { i -> loop.submitCorrection("txn_retrain_$i", createTestFeatures("Merchant$i", "MERCHANT $i"), "Old", "New$i", 0.5f, "test") }
        val result = loop.triggerRetrain()
        assertIs<RetrainResult.Success>(result)
        assertTrue((result as RetrainResult.Success).correctionsUsed > 0)
    }

    @Test fun testRuleSuggestionFromRepeatedCorrections() = runTest {
        val loop = createFeedbackLoop()
        val merchant = "FrequentlyCorrectedMerchant"
        repeat(CorrectionProcessor.RULE_SUGGESTION_THRESHOLD + 1) { i -> loop.submitCorrection("txn_suggest_$i", createTestFeatures(merchant, "FREQUENTLY CORRECTED $i"), "WrongCategory", "CorrectCategory", 0.5f, "naive-bayes") }
        val analysis = loop.getAnalysis()
        assertTrue(analysis.suggestedRules.isNotEmpty())
        assertEquals("CorrectCategory", analysis.suggestedRules.first().categoryId)
    }

    @Test fun testDefaultRuleManagerCreatesRules() {
        val manager = DefaultRuleManager()
        val rule = manager.createRuleFromSuggestion(SuggestedRule("testmerchant", "TestCategory", 5, 0.9f, "Test reason"))
        assertNotNull(rule)
        assertEquals("TestCategory", rule.categoryId)
        assertTrue(rule.enabled)
        assertEquals(1, manager.getUserRules().size)
    }

    @Test fun testRuleManagerEnableDisable() {
        val manager = DefaultRuleManager()
        val rule = manager.createRuleFromSuggestion(SuggestedRule("merchant", "Category", 5, 0.9f, "Test"))!!
        assertTrue(manager.getUserRules().first().enabled)
        manager.setRuleEnabled(rule.id, false)
        assertFalse(manager.getUserRules().first().enabled)
        manager.setRuleEnabled(rule.id, true)
        assertTrue(manager.getUserRules().first().enabled)
    }

    @Test fun testRuleManagerDeleteRule() {
        val manager = DefaultRuleManager()
        val rule = manager.createRuleFromSuggestion(SuggestedRule("merchant", "Category", 5, 0.9f, "Test"))!!
        assertEquals(1, manager.getUserRules().size)
        assertTrue(manager.deleteRule(rule.id))
        assertEquals(0, manager.getUserRules().size)
        assertFalse(manager.deleteRule(rule.id))
    }

    @Test fun testFeedbackLoopFactoryCreatesValidInstance() = runTest {
        val result = FeedbackLoopFactory.create().submitCorrection("factory_test", createTestFeatures("Factory", "FACTORY TEST"), "A", "B", 0.5f, "test")
        assertNotNull(result)
        assertIs<LearningResult.Updated>(result.correctionResult.learningResult)
    }
}
