package com.ledgerlens.categorization

import com.ledgerlens.domain.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClassifierChainTest {

    private val extractor = FeatureExtractor()

    @Test
    fun `rule-based classifier matches grocery stores`() {
        val classifier = RuleBasedClassifier()
        val features = extractor.extract(
            descriptionRaw = "WALMART SUPERCENTER #1234",
            amount = Money.fromMinorUnits(-8500, "USD")
        )

        val result = classifier.classify(features)
        assertEquals("Groceries", result.categoryId)
        assertTrue(result.confidence >= 0.9f)
        assertEquals("rule-based", result.explanation.classifierUsed)
    }

    @Test
    fun `rule-based classifier matches restaurants`() {
        val classifier = RuleBasedClassifier()
        val features = extractor.extract(
            descriptionRaw = "STARBUCKS STORE 12345",
            amount = Money.fromMinorUnits(-650, "USD")
        )

        val result = classifier.classify(features)
        assertEquals("Dining", result.categoryId)
        assertTrue(result.confidence >= 0.9f)
    }

    @Test
    fun `rule-based classifier matches streaming services`() {
        val classifier = RuleBasedClassifier()
        val features = extractor.extract(
            descriptionRaw = "NETFLIX.COM",
            amount = Money.fromMinorUnits(-1599, "USD")
        )

        val result = classifier.classify(features)
        assertEquals("Entertainment", result.categoryId)
        assertTrue(result.confidence >= 0.9f)
    }

    @Test
    fun `rule-based classifier returns unknown for unmatched`() {
        val classifier = RuleBasedClassifier()
        val features = extractor.extract(
            descriptionRaw = "OBSCURE MERCHANT XYZ123",
            amount = Money.fromMinorUnits(-5000, "USD")
        )

        val result = classifier.classify(features)
        assertEquals(ClassificationResult.UNKNOWN_CATEGORY_ID, result.categoryId)
    }

    @Test
    fun `classifier chain tries classifiers in priority order`() {
        val highPriorityClassifier = object : TransactionClassifier {
            override val name = "high-priority"
            override val priority = 100
            override fun classify(features: TransactionFeatures): ClassificationResult {
                return ClassificationResult(
                    categoryId = "high-priority-category",
                    confidence = 0.95f,
                    explanation = ClassificationExplanation(
                        classifierUsed = name,
                        reason = "High priority match"
                    )
                )
            }
        }

        val lowPriorityClassifier = object : TransactionClassifier {
            override val name = "low-priority"
            override val priority = 10
            override fun classify(features: TransactionFeatures): ClassificationResult {
                return ClassificationResult(
                    categoryId = "low-priority-category",
                    confidence = 0.99f,
                    explanation = ClassificationExplanation(
                        classifierUsed = name,
                        reason = "Low priority match"
                    )
                )
            }
        }

        val chain = ClassifierChain(
            classifiers = listOf(lowPriorityClassifier, highPriorityClassifier)
        )

        val features = createTestFeatures()
        val result = chain.classify(features)

        assertEquals("high-priority-category", result.categoryId)
    }

    @Test
    fun `classifier chain falls back when confidence is low`() {
        val lowConfidenceClassifier = object : TransactionClassifier {
            override val name = "low-confidence"
            override val priority = 100
            override fun classify(features: TransactionFeatures): ClassificationResult {
                return ClassificationResult(
                    categoryId = "low-conf-category",
                    confidence = 0.3f,
                    explanation = ClassificationExplanation(
                        classifierUsed = name,
                        reason = "Low confidence"
                    )
                )
            }
        }

        val highConfidenceClassifier = object : TransactionClassifier {
            override val name = "high-confidence"
            override val priority = 50
            override fun classify(features: TransactionFeatures): ClassificationResult {
                return ClassificationResult(
                    categoryId = "high-conf-category",
                    confidence = 0.9f,
                    explanation = ClassificationExplanation(
                        classifierUsed = name,
                        reason = "High confidence"
                    )
                )
            }
        }

        val chain = ClassifierChain(
            classifiers = listOf(lowConfidenceClassifier, highConfidenceClassifier),
            confidenceThreshold = 0.7f
        )

        val features = createTestFeatures()
        val result = chain.classify(features)

        assertEquals("high-conf-category", result.categoryId)
    }

    @Test
    fun `classifier chain returns best result when none meet threshold`() {
        val mediumConfClassifier = object : TransactionClassifier {
            override val name = "medium"
            override val priority = 100
            override fun classify(features: TransactionFeatures): ClassificationResult {
                return ClassificationResult(
                    categoryId = "medium-category",
                    confidence = 0.5f,
                    explanation = ClassificationExplanation(
                        classifierUsed = name,
                        reason = "Medium confidence"
                    )
                )
            }
        }

        val lowConfClassifier = object : TransactionClassifier {
            override val name = "low"
            override val priority = 50
            override fun classify(features: TransactionFeatures): ClassificationResult {
                return ClassificationResult(
                    categoryId = "low-category",
                    confidence = 0.3f,
                    explanation = ClassificationExplanation(
                        classifierUsed = name,
                        reason = "Low confidence"
                    )
                )
            }
        }

        val chain = ClassifierChain(
            classifiers = listOf(mediumConfClassifier, lowConfClassifier),
            confidenceThreshold = 0.8f
        )

        val features = createTestFeatures()
        val result = chain.classify(features)

        assertEquals("medium-category", result.categoryId)
        assertEquals(0.5f, result.confidence)
    }

    @Test
    fun `classifier chain skips classifiers that cannot classify`() {
        val cannotClassify = object : TransactionClassifier {
            override val name = "cannot"
            override val priority = 100
            override fun canClassify(features: TransactionFeatures) = false
            override fun classify(features: TransactionFeatures): ClassificationResult {
                throw IllegalStateException("Should not be called")
            }
        }

        val canClassify = object : TransactionClassifier {
            override val name = "can"
            override val priority = 50
            override fun classify(features: TransactionFeatures): ClassificationResult {
                return ClassificationResult(
                    categoryId = "can-category",
                    confidence = 0.8f,
                    explanation = ClassificationExplanation(
                        classifierUsed = name,
                        reason = "Can classify"
                    )
                )
            }
        }

        val chain = ClassifierChain(classifiers = listOf(cannotClassify, canClassify))
        val features = createTestFeatures()
        val result = chain.classify(features)

        assertEquals("can-category", result.categoryId)
    }

    @Test
    fun `classifyWithAllResults returns results from all classifiers`() {
        val chain = ClassifierChain.createDefault()
        val features = extractor.extract(
            descriptionRaw = "WALMART SUPERCENTER",
            amount = Money.fromMinorUnits(-5000, "USD")
        )

        val chainResult = chain.classifyWithAllResults(features)

        assertTrue(chainResult.classifierResults.containsKey("rule-based"))
        assertEquals("Groceries", chainResult.finalResult.categoryId)
    }

    @Test
    fun `chain result detects consensus`() {
        val results = ChainClassificationResult(
            finalResult = ClassificationResult(
                categoryId = "Groceries",
                confidence = 0.9f,
                explanation = ClassificationExplanation("test", "test")
            ),
            classifierResults = mapOf(
                "a" to ClassificationResult(
                    categoryId = "Groceries",
                    confidence = 0.9f,
                    explanation = ClassificationExplanation("a", "a")
                ),
                "b" to ClassificationResult(
                    categoryId = "Groceries",
                    confidence = 0.85f,
                    explanation = ClassificationExplanation("b", "b")
                )
            )
        )

        assertTrue(results.isConsensus())
    }

    @Test
    fun `chain result detects disagreement`() {
        val results = ChainClassificationResult(
            finalResult = ClassificationResult(
                categoryId = "Groceries",
                confidence = 0.9f,
                explanation = ClassificationExplanation("test", "test")
            ),
            classifierResults = mapOf(
                "a" to ClassificationResult(
                    categoryId = "Groceries",
                    confidence = 0.9f,
                    explanation = ClassificationExplanation("a", "a")
                ),
                "b" to ClassificationResult(
                    categoryId = "Shopping",
                    confidence = 0.85f,
                    explanation = ClassificationExplanation("b", "b")
                )
            )
        )

        assertFalse(results.isConsensus())
    }

    @Test
    fun `default chain includes rule-based and naive bayes`() {
        val chain = ClassifierChain.createDefault()
        val classifierNames = chain.getClassifiers().map { it.name }

        assertTrue(classifierNames.contains("rule-based"))
        assertTrue(classifierNames.contains("naive-bayes"))
        assertTrue(classifierNames.contains("fallback"))
    }

    @Test
    fun `classification result identifies high confidence`() {
        val result = ClassificationResult(
            categoryId = "test",
            confidence = 0.9f,
            explanation = ClassificationExplanation("test", "test")
        )
        assertTrue(result.isHighConfidence)
        assertFalse(result.needsReview)
    }

    @Test
    fun `classification result identifies needs review`() {
        val result = ClassificationResult(
            categoryId = "test",
            confidence = 0.4f,
            explanation = ClassificationExplanation("test", "test")
        )
        assertFalse(result.isHighConfidence)
        assertTrue(result.needsReview)
    }

    private fun createTestFeatures(): TransactionFeatures {
        return TransactionFeatures(
            merchantNormalized = "Test Merchant",
            descriptionRaw = "TEST MERCHANT PURCHASE",
            descriptionTokens = listOf("test", "merchant", "purchase"),
            amountCents = -5000,
            amountBucket = AmountBucket.MEDIUM,
            isDebit = true,
            dayOfWeek = 1,
            dayOfMonth = 15
        )
    }
}
