package com.ledgerlens.categorization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.datetime.Clock

class IncrementalLearnerTest {
    private fun createTestFeatures(
        merchant: String = "Test Merchant",
        description: String = "Test transaction",
        amountCents: Long = -1500L
    ) =
        TransactionFeatures(merchant, description, description.lowercase().split(" "), amountCents, AmountBucket.fromCents(amountCents), amountCents < 0, 1, 15)

    private fun createCorrection(
        features: TransactionFeatures,
        oldCategory: String,
        newCategory: String,
        confidence: Float = 0.75f
    ) =
        CategoryCorrection("corr_test_${Clock.System.now().toEpochMilliseconds()}", "txn_test", oldCategory, newCategory, Clock.System.now(), features, confidence, "naive-bayes")

    @Test fun testLearnFromCorrectionUpdatesModel() {
        val classifier = NaiveBayesClassifier()
        val learner = IncrementalLearner(classifier)
        val result = learner.learnFromCorrection(createCorrection(createTestFeatures("Starbucks", "STARBUCKS COFFEE SHOP"), "Shopping", "Dining"))
        assertIs<LearningResult.Updated>(result)
        assertTrue((result as LearningResult.Updated).weightChanges > 0)
        assertTrue(classifier.getModel().categories.contains("Dining"))
    }

    @Test fun testHighConfidenceMissGetsHigherWeight() {
        val classifier = NaiveBayesClassifier()
        val learner = IncrementalLearner(classifier)
        val features = createTestFeatures("NewStore", "NEW STORE PURCHASE")
        val highResult = learner.learnFromCorrection(createCorrection(features, "Wrong", "Right", confidence = 0.9f)) as LearningResult.Updated
        val classifier2 = NaiveBayesClassifier()
        val learner2 = IncrementalLearner(classifier2)
        val lowResult = learner2.learnFromCorrection(createCorrection(features, "Wrong", "Right", confidence = 0.4f)) as LearningResult.Updated
        assertTrue(highResult.weightChanges >= lowResult.weightChanges)
    }

    @Test fun testNoChangeForSameCategory() {
        val classifier = NaiveBayesClassifier()
        val learner = IncrementalLearner(classifier)
        val result = learner.learnFromCorrection(createCorrection(createTestFeatures("McDonalds", "MCDONALDS RESTAURANT"), "Dining", "Dining"))
        assertEquals(LearningResult.NoChange, result)
    }

    @Test fun testReinforcementAddsTrainingExample() {
        val classifier = NaiveBayesClassifier()
        val learner = IncrementalLearner(classifier)
        val modelBefore = classifier.getModel()
        learner.reinforcePrediction(createTestFeatures("GasStation", "SHELL GAS STATION"), "Transportation")
        val modelAfter = classifier.getModel()
        assertTrue(modelAfter.categories.contains("Transportation"))
        assertTrue(modelAfter.totalExamples > modelBefore.totalExamples)
    }

    @Test fun testConfidenceAdjustmentForFrequentlyCorrectedMerchant() {
        val classifier = NaiveBayesClassifier()
        val learner = IncrementalLearner(classifier)
        assertEquals(1.0f, learner.getConfidenceAdjustment("UnknownMerchant", null))
        assertEquals(1.0f, learner.getConfidenceAdjustment("LowCorrection", CorrectionStats(100, 5, emptyMap(), null)))
        assertTrue(learner.getConfidenceAdjustment("HighCorrection", CorrectionStats(100, 50, emptyMap(), null)) < 1.0f)
    }

    @Test fun testCustomLearningConfigDisablesReinforcement() {
        val classifier = NaiveBayesClassifier()
        val learner = IncrementalLearner(classifier, LearningConfig(enableReinforcement = false))
        val modelBefore = classifier.getModel()
        learner.reinforcePrediction(createTestFeatures("Test", "TEST"), "Category")
        assertEquals(modelBefore.totalExamples, classifier.getModel().totalExamples)
    }

    @Test fun testMultipleCorrectionsImproveClassification() {
        val classifier = NaiveBayesClassifier()
        val learner = IncrementalLearner(classifier)
        val coffeeFeatures = createTestFeatures("CoffeeShop", "JAVA COFFEE HOUSE CAFE")
        classifier.train(coffeeFeatures, "Shopping")
        classifier.train(coffeeFeatures, "Shopping")
        repeat(5) { learner.learnFromCorrection(createCorrection(coffeeFeatures, "Shopping", "Dining", 0.7f)) }
        val model = classifier.getModel()
        assertTrue((model.categoryCounts["Dining"] ?: 0) > (model.categoryCounts["Shopping"] ?: 0))
    }
}
