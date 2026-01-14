package com.ledgerlens.categorization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NaiveBayesClassifierTest {

    @Test
    fun `empty model returns unknown classification`() {
        val classifier = NaiveBayesClassifier()
        val features = createTestFeatures("Test Store", -5000)

        val result = classifier.classify(features)
        assertEquals(ClassificationResult.UNKNOWN_CATEGORY_ID, result.categoryId)
    }

    @Test
    fun `trained classifier returns correct category`() {
        val classifier = NaiveBayesClassifier()

        // Train with grocery examples
        repeat(5) {
            classifier.train(createTestFeatures("Walmart", -8000), "Groceries")
            classifier.train(createTestFeatures("Kroger", -6500), "Groceries")
        }

        // Train with dining examples
        repeat(5) {
            classifier.train(createTestFeatures("McDonalds", -1200), "Dining")
            classifier.train(createTestFeatures("Starbucks", -650), "Dining")
        }

        // Test classification
        val groceryFeatures = createTestFeatures("Walmart Supercenter", -9500)
        val groceryResult = classifier.classify(groceryFeatures)

        assertEquals("Groceries", groceryResult.categoryId)
        assertTrue(groceryResult.confidence > 0.5f)
    }

    @Test
    fun `model learns from training examples`() {
        val model = NaiveBayesModel()
        val features = createTestFeatures("Amazon", -5000)

        val updatedModel = model.addExample(features, "Shopping")

        assertEquals(1, updatedModel.totalExamples)
        assertTrue(updatedModel.categories.contains("Shopping"))
        assertEquals(1, updatedModel.categoryCounts["Shopping"])
    }

    @Test
    fun `model accumulates multiple examples`() {
        var model = NaiveBayesModel()

        model = model.addExample(createTestFeatures("Amazon", -5000), "Shopping")
        model = model.addExample(createTestFeatures("Target", -3500), "Shopping")
        model = model.addExample(createTestFeatures("Starbucks", -650), "Dining")

        assertEquals(3, model.totalExamples)
        assertEquals(2, model.categoryCounts["Shopping"])
        assertEquals(1, model.categoryCounts["Dining"])
    }

    @Test
    fun `classifier provides alternatives`() {
        val classifier = NaiveBayesClassifier()

        // Train with multiple categories
        repeat(3) {
            classifier.train(createTestFeatures("Walmart", -8000), "Groceries")
            classifier.train(createTestFeatures("Target", -5000), "Shopping")
            classifier.train(createTestFeatures("Starbucks", -600), "Dining")
        }

        val features = createTestFeatures("Target Store", -4500)
        val result = classifier.classify(features)

        assertTrue(result.alternatives.isNotEmpty())
        assertTrue(result.alternatives.size <= 3)
    }

    @Test
    fun `merchant priors are tracked`() {
        var model = NaiveBayesModel()

        model = model.addExample(createTestFeatures("Starbucks", -500), "Dining")
        model = model.addExample(createTestFeatures("Starbucks", -650), "Dining")
        model = model.addExample(createTestFeatures("Starbucks", -400), "Dining")

        assertTrue(model.merchantPriors.containsKey("Starbucks"))
        val starbucksPriors = model.merchantPriors["Starbucks"]!!
        assertEquals(1.0f, starbucksPriors["Dining"]!!, 0.01f)
    }

    @Test
    fun `amount bucket counts are tracked`() {
        var model = NaiveBayesModel()

        model = model.addExample(createTestFeatures("Coffee Shop", -350), "Dining") // MICRO
        model = model.addExample(createTestFeatures("Restaurant", -2000), "Dining") // SMALL

        assertTrue(model.amountBucketCounts.containsKey(AmountBucket.MICRO))
        assertEquals(1, model.amountBucketCounts[AmountBucket.MICRO]!!["Dining"])
    }

    @Test
    fun `classifier explanation includes matched tokens`() {
        val classifier = NaiveBayesClassifier()

        classifier.train(createTestFeatures("Coffee Shop Morning", -500), "Dining")
        classifier.train(createTestFeatures("Coffee House", -600), "Dining")
        classifier.train(createTestFeatures("Coffee Place", -450), "Dining")

        val features = createTestFeatures("Coffee Shop Purchase", -550)
        val result = classifier.classify(features)

        assertEquals("naive-bayes", result.explanation.classifierUsed)
        assertTrue(result.explanation.tokenMatches.isNotEmpty())
    }

    @Test
    fun `canClassify returns false for empty model`() {
        val classifier = NaiveBayesClassifier()
        val features = createTestFeatures("Test", -1000)

        assertFalse(classifier.canClassify(features))
    }

    @Test
    fun `canClassify returns true for trained model`() {
        val classifier = NaiveBayesClassifier()
        classifier.train(createTestFeatures("Test", -1000), "TestCategory")

        val features = createTestFeatures("Another Test", -2000)
        assertTrue(classifier.canClassify(features))
    }

    @Test
    fun `loadModel replaces existing model`() {
        val classifier = NaiveBayesClassifier()
        classifier.train(createTestFeatures("Old", -1000), "OldCategory")

        val newModel = NaiveBayesModel().addExample(
            createTestFeatures("New", -2000), 
            "NewCategory"
        )
        classifier.loadModel(newModel)

        assertEquals(newModel, classifier.getModel())
        assertTrue(classifier.getModel().categories.contains("NewCategory"))
        assertFalse(classifier.getModel().categories.contains("OldCategory"))
    }

    private fun createTestFeatures(merchant: String, amountCents: Long): TransactionFeatures {
        val tokens = merchant.lowercase().split(" ").filter { it.length >= 2 }
        return TransactionFeatures(
            merchantNormalized = merchant,
            descriptionRaw = "$merchant PURCHASE",
            descriptionTokens = tokens,
            amountCents = amountCents,
            amountBucket = AmountBucket.fromCents(amountCents),
            isDebit = amountCents < 0,
            dayOfWeek = 1,
            dayOfMonth = 15
        )
    }
}
