package com.ledgerlens.categorization.pipeline

import com.ledgerlens.categorization.MerchantPriorProvider
import com.ledgerlens.categorization.MerchantCategoryDistribution
import com.ledgerlens.categorization.CategoryProbability
import kotlin.test.*

class CategorizationPipelineTest {
    private fun createFeatures(merchant: String = "Test", description: String = "Test", amountCents: Long = -2500) = TransactionFeatures(
        merchant, description, description.lowercase().split(" "), amountCents, AmountBucket.fromCents(amountCents), amountCents < 0, 1, 15
    )

    private fun createPipeline(rules: List<ClassificationRule> = emptyList(), merchantPriorProvider: MerchantPriorProvider = EmptyMerchantPriorProvider, config: CategorizationConfig = CategorizationConfig.default()) =
        CategorizationPipelineImpl(RuleBasedClassifier(rules), FallbackClassifier, merchantPriorProvider, config)

    @Test fun `categorize returns unknown when no classifiers match`() {
        val result = createPipeline().categorize(createFeatures(), "tx-1")
        assertEquals(ClassificationResult.UNKNOWN_CATEGORY_ID, result.categoryId)
        assertEquals(CategorizationAction.QUEUE_FOR_REVIEW, result.action)
    }

    @Test fun `categorize uses rule classifier first`() {
        val rules = listOf(MerchantContainsRule("test", "Groceries", listOf("walmart"), 0.95f))
        val result = createPipeline(rules = rules).categorize(createFeatures(merchant = "Walmart"), "tx-1")
        assertEquals("Groceries", result.categoryId)
        assertEquals(PipelineStage.USER_RULES, result.usedStage)
    }

    @Test fun `categorize uses merchant prior when confidence is high`() {
        val provider = object : MerchantPriorProvider {
            override fun getDistribution(merchantId: String) = MerchantCategoryDistribution(merchantId, listOf(CategoryProbability("Groceries", 0.9f, 10, 10f)), 10, 10f)
            override fun updatePrior(merchantId: String, categoryId: String) {}
        }
        val result = createPipeline(merchantPriorProvider = provider).categorize(createFeatures(), "tx-1")
        assertEquals("Groceries", result.categoryId)
        assertEquals(PipelineStage.MERCHANT_PRIORS, result.usedStage)
    }

    @Test fun `categorize determines action based on confidence`() {
        val rules = listOf(MerchantContainsRule("h", "C1", listOf("high"), 0.95f), MerchantContainsRule("m", "C2", listOf("med"), 0.7f), MerchantContainsRule("l", "C3", listOf("low"), 0.3f))
        val pipeline = createPipeline(rules = rules)
        assertEquals(CategorizationAction.AUTO_APPLY, pipeline.categorize(createFeatures(merchant = "high"), "tx-1").action)
        assertEquals(CategorizationAction.SUGGEST, pipeline.categorize(createFeatures(merchant = "med"), "tx-2").action)
        assertEquals(CategorizationAction.QUEUE_FOR_REVIEW, pipeline.categorize(createFeatures(merchant = "low"), "tx-3").action)
    }

    @Test fun `categorize respects disabled stages`() {
        val rules = listOf(MerchantContainsRule("r", "Groceries", listOf("test"), 0.95f))
        val config = CategorizationConfig(enabledStages = setOf(PipelineStage.ML_CLASSIFICATION))
        val result = createPipeline(rules = rules, config = config).categorize(createFeatures(merchant = "test"), "tx-1")
        assertTrue(result.usedStage != PipelineStage.USER_RULES)
    }

    @Test fun `categorizeBatch processes multiple transactions`() {
        val rules = listOf(MerchantContainsRule("r1", "Groceries", listOf("walmart"), 0.95f), MerchantContainsRule("r2", "Dining", listOf("starbucks"), 0.95f))
        val results = createPipeline(rules = rules).categorizeBatch(listOf(
            TransactionInput("tx-1", createFeatures(merchant = "walmart")),
            TransactionInput("tx-2", createFeatures(merchant = "starbucks")),
            TransactionInput("tx-3", createFeatures(merchant = "unknown"))
        ))
        assertEquals(3, results.size)
        assertEquals("Groceries", results[0].categoryId)
        assertEquals("Dining", results[1].categoryId)
    }

    @Test fun `updateConfig changes behavior`() {
        val rules = listOf(MerchantContainsRule("r", "Groceries", listOf("test"), 0.75f))
        val pipeline = createPipeline(rules = rules)
        assertEquals(CategorizationAction.SUGGEST, pipeline.categorize(createFeatures(merchant = "test"), "tx-1").action)
        pipeline.updateConfig(pipeline.config.copy(highConfidenceThreshold = 0.7f))
        assertEquals(CategorizationAction.AUTO_APPLY, pipeline.categorize(createFeatures(merchant = "test"), "tx-2").action)
    }

    @Test fun `getStats returns pipeline statistics`() {
        val rules = listOf(MerchantContainsRule("r", "Groceries", listOf("walmart"), 0.95f))
        val pipeline = createPipeline(rules = rules)
        pipeline.categorize(createFeatures(merchant = "walmart"), "tx-1")
        pipeline.categorize(createFeatures(merchant = "unknown"), "tx-2")
        assertEquals(2, pipeline.getStats().totalProcessed)
    }
}

class CategorizationConfigTest {
    @Test fun `default config has valid thresholds`() { val c = CategorizationConfig.default(); assertTrue(c.highConfidenceThreshold > c.reviewThreshold) }
    @Test fun `determineAction returns correct action`() {
        val c = CategorizationConfig(highConfidenceThreshold = 0.85f, reviewThreshold = 0.5f)
        assertEquals(CategorizationAction.AUTO_APPLY, c.determineAction(0.9f))
        assertEquals(CategorizationAction.SUGGEST, c.determineAction(0.7f))
        assertEquals(CategorizationAction.QUEUE_FOR_REVIEW, c.determineAction(0.3f))
    }
    @Test fun `isStageEnabled returns correct value`() {
        val c = CategorizationConfig(enabledStages = setOf(PipelineStage.USER_RULES))
        assertTrue(c.isStageEnabled(PipelineStage.USER_RULES))
        assertFalse(c.isStageEnabled(PipelineStage.MERCHANT_PRIORS))
    }
    @Test fun `toStrict increases thresholds`() { val c = CategorizationConfig.default(); assertTrue(c.toStrict().highConfidenceThreshold > c.highConfidenceThreshold) }
}

class PipelineStageTest {
    @Test fun `stages have correct order`() {
        val ordered = PipelineStage.inOrder()
        assertEquals(PipelineStage.USER_RULES, ordered[0])
        assertEquals(PipelineStage.MERCHANT_PRIORS, ordered[1])
        assertEquals(PipelineStage.ML_CLASSIFICATION, ordered[2])
    }
}
