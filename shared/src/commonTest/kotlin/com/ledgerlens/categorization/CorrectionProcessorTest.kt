package com.ledgerlens.categorization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class CorrectionProcessorTest {
    private fun createTestFeatures(
        merchant: String = "Test Merchant",
        description: String = "Test transaction",
        amountCents: Long = -1500L
    ) =
        TransactionFeatures(merchant, description, description.lowercase().split(" "), amountCents, AmountBucket.fromCents(amountCents), amountCents < 0, 1, 15)

    private fun createProcessor(): Triple<CorrectionProcessor, InMemoryCorrectionRepository, NaiveBayesClassifier> {
        val repo = InMemoryCorrectionRepository()
        val stats = InMemoryPredictionStatsRepository()
        val classifier = NaiveBayesClassifier()
        val learner = IncrementalLearner(classifier)
        return Triple(CorrectionProcessor(repo, stats, learner), repo, classifier)
    }

    @Test fun testProcessCorrectionStoresCorrection() = runTest {
        val (processor, repo, _) = createProcessor()
        val result = processor.processCorrection("txn_001", createTestFeatures("Starbucks", "STARBUCKS COFFEE"), "Shopping", "Dining", 0.75f, "naive-bayes")
        assertEquals(1, repo.count())
        assertEquals("Shopping", result.correction.oldCategoryId)
        assertEquals("Dining", result.correction.newCategoryId)
        assertTrue(result.correction.isActualCorrection)
    }

    @Test fun testProcessCorrectionUpdatesModel() = runTest {
        val (processor, _, classifier) = createProcessor()
        val result = processor.processCorrection("txn_001", createTestFeatures("Starbucks", "STARBUCKS COFFEE"), "Shopping", "Dining", 0.75f, "naive-bayes")
        assertTrue(result.learningResult is LearningResult.Updated)
        assertTrue(classifier.getModel().categories.contains("Dining"))
    }

    @Test fun testConfirmationReinforcesPrediction() = runTest {
        val (processor, repo, _) = createProcessor()
        val result = processor.processCorrection("txn_002", createTestFeatures("McDonald's", "MCDONALDS"), "Dining", "Dining", 0.9f, "naive-bayes")
        assertFalse(result.correction.isActualCorrection)
        assertEquals(LearningResult.Reinforced, result.learningResult)
        assertEquals(1, repo.count())
    }

    @Test fun testHighConfidenceMissIsTracked() = runTest {
        val (processor, _, _) = createProcessor()
        val result = processor.processCorrection("txn_003", createTestFeatures("Amazon", "AMAZON MARKETPLACE"), "Entertainment", "Shopping", 0.92f, "naive-bayes")
        assertTrue(result.correction.wasHighConfidenceMiss)
    }

    @Test fun testBatchProcessingMultipleCorrections() = runTest {
        val (processor, repo, _) = createProcessor()
        val corrections = listOf(
            PendingCorrection("txn_b1", createTestFeatures("Walmart", "WALMART GROCERY"), "Shopping", "Groceries", 0.7f, "rule-based"),
            PendingCorrection("txn_b2", createTestFeatures("Shell", "SHELL GAS STATION"), "Shopping", "Transportation", 0.6f, "naive-bayes")
        )
        val result = processor.processBatch(corrections)
        assertEquals(2, result.successCount)
        assertEquals(0, result.failureCount)
        assertEquals(2, repo.count())
    }

    @Test fun testRuleSuggestionAfterRepeatedCorrections() = runTest {
        val (processor, _, _) = createProcessor()
        val merchant = "NewCoffeeShop"
        repeat(CorrectionProcessor.RULE_SUGGESTION_THRESHOLD) { i -> processor.processCorrection("txn_rule_$i", createTestFeatures(merchant, "NEW COFFEE SHOP #$i"), "Shopping", "Dining", 0.5f, "naive-bayes") }
        val result = processor.processCorrection("txn_rule_final", createTestFeatures(merchant, "NEW COFFEE SHOP FINAL"), "Shopping", "Dining", 0.5f, "naive-bayes")
        assertNotNull(result.suggestedRule)
        assertEquals(merchant, result.suggestedRule?.merchantPattern)
        assertEquals("Dining", result.suggestedRule?.categoryId)
    }

    @Test fun testAnalyzeCorrectionsGroupsByMerchant() = runTest {
        val (processor, _, _) = createProcessor()
        processor.processCorrection("txn_a1", createTestFeatures("MerchantA", "MERCHANT A PURCHASE"), "Shopping", "Groceries", 0.6f, "naive-bayes")
        processor.processCorrection("txn_a2", createTestFeatures("MerchantA", "MERCHANT A PURCHASE 2"), "Shopping", "Groceries", 0.6f, "naive-bayes")
        processor.processCorrection("txn_b1", createTestFeatures("MerchantB", "MERCHANT B PURCHASE"), "Dining", "Entertainment", 0.7f, "naive-bayes")
        val analysis = processor.analyzeCorrections()
        assertEquals(3, analysis.totalCorrections)
        assertEquals(2, analysis.merchantStats.size)
        assertEquals(2, analysis.merchantStats["merchanta"]?.totalCorrections)
    }
}
