package com.ledgerlens.categorization.pipeline

import kotlin.time.TimeSource

class CategorizationPipelineImpl(
    private val ruleClassifier: RuleBasedClassifier,
    private val mlClassifier: TransactionClassifier,
    private val merchantPriorProvider: MerchantPriorProvider,
    initialConfig: CategorizationConfig = CategorizationConfig.default(),
    private val eventListener: PipelineEventListener? = null
) : CategorizationPipeline {
    private val mutableConfig = MutableCategorizationConfig(initialConfig)
    private val statsCollector = PipelineStatsCollector()
    override val config get() = mutableConfig.config
    override fun updateConfig(newConfig: CategorizationConfig) { mutableConfig.update(newConfig) }

    override fun categorize(features: TransactionFeatures, transactionId: String): PipelineResult {
        val start = TimeSource.Monotonic.markNow()
        val stageResults = mutableMapOf<PipelineStage, StageResult>()
        var finalResult: ClassificationResult? = null
        var usedStage: PipelineStage? = null
        try {
            if (config.isStageEnabled(PipelineStage.USER_RULES)) {
                val r = executeUserRulesStage(features, transactionId)
                stageResults[PipelineStage.USER_RULES] = r
                if (r.hasResult && r.classification!!.isHighConfidence) { finalResult = r.classification; usedStage = PipelineStage.USER_RULES }
            }
            if (finalResult == null && config.isStageEnabled(PipelineStage.MERCHANT_PRIORS)) {
                val r = executeMerchantPriorsStage(features, transactionId)
                stageResults[PipelineStage.MERCHANT_PRIORS] = r
                if (r.hasResult && r.classification!!.confidence >= config.merchantPriorMinConfidence) { finalResult = r.classification; usedStage = PipelineStage.MERCHANT_PRIORS }
            }
            if (finalResult == null && config.isStageEnabled(PipelineStage.ML_CLASSIFICATION)) {
                val r = executeMLClassificationStage(features, transactionId)
                stageResults[PipelineStage.ML_CLASSIFICATION] = r
                if (r.hasResult) { finalResult = r.classification; usedStage = PipelineStage.ML_CLASSIFICATION }
            }
            if (finalResult == null) finalResult = ClassificationResult.unknown()
            val action = config.determineAction(finalResult.confidence)
            if (config.isStageEnabled(PipelineStage.REVIEW_QUEUE) && action == CategorizationAction.QUEUE_FOR_REVIEW) {
                stageResults[PipelineStage.REVIEW_QUEUE] = StageResult(PipelineStage.REVIEW_QUEUE, null, true, false, null, 0)
            }
            val result = PipelineResult(transactionId, finalResult, action, stageResults, start.elapsedNow().inWholeMilliseconds, usedStage)
            statsCollector.recordResult(result)
            eventListener?.onCategorizationComplete(result)
            return result
        } catch (e: Exception) {
            eventListener?.onError(transactionId, usedStage, e)
            return PipelineResult.unknown(transactionId, start.elapsedNow().inWholeMilliseconds)
        }
    }

    override fun categorizeBatch(transactions: List<TransactionInput>) = transactions.map { categorize(it.features, it.transactionId) }
    override fun getStats() = statsCollector.getStats()

    private fun executeUserRulesStage(features: TransactionFeatures, transactionId: String): StageResult {
        eventListener?.onStageStarted(transactionId, PipelineStage.USER_RULES)
        val start = TimeSource.Monotonic.markNow()
        val c = ruleClassifier.classify(features)
        val t = start.elapsedNow().inWholeMilliseconds
        val r = if (c.categoryId != ClassificationResult.UNKNOWN_CATEGORY_ID) StageResult(PipelineStage.USER_RULES, c, true, false, null, t) else StageResult.noResult(PipelineStage.USER_RULES, t)
        eventListener?.onStageCompleted(transactionId, PipelineStage.USER_RULES, r)
        return r
    }

    private fun executeMerchantPriorsStage(features: TransactionFeatures, transactionId: String): StageResult {
        eventListener?.onStageStarted(transactionId, PipelineStage.MERCHANT_PRIORS)
        val start = TimeSource.Monotonic.markNow()
        val dist = merchantPriorProvider.getDistribution(features.merchantNormalized)
        val t = start.elapsedNow().inWholeMilliseconds
        if (!dist.isReliable || dist.mostLikelyCategory == null) { val r = StageResult.noResult(PipelineStage.MERCHANT_PRIORS, t); eventListener?.onStageCompleted(transactionId, PipelineStage.MERCHANT_PRIORS, r); return r }
        val ml = dist.mostLikelyCategory!!
        if (ml.probability < config.merchantPriorMinConfidence) { val r = StageResult(PipelineStage.MERCHANT_PRIORS, null, false, true, "Below threshold", t); eventListener?.onStageCompleted(transactionId, PipelineStage.MERCHANT_PRIORS, r); return r }
        val c = ClassificationResult(ml.categoryId, ml.probability, dist.probabilities.filter { it.categoryId != ml.categoryId }.take(3).map { CategoryScore(it.categoryId, it.probability) },
            ClassificationExplanation("merchant-prior", "Based on ${dist.totalObservations} previous transactions", merchantPrior = ml.probability))
        val r = StageResult(PipelineStage.MERCHANT_PRIORS, c, true, false, null, t)
        eventListener?.onStageCompleted(transactionId, PipelineStage.MERCHANT_PRIORS, r)
        return r
    }

    private fun executeMLClassificationStage(features: TransactionFeatures, transactionId: String): StageResult {
        eventListener?.onStageStarted(transactionId, PipelineStage.ML_CLASSIFICATION)
        val start = TimeSource.Monotonic.markNow()
        if (!mlClassifier.canClassify(features)) { val r = StageResult.skipped(PipelineStage.ML_CLASSIFICATION, "Cannot classify"); eventListener?.onStageCompleted(transactionId, PipelineStage.ML_CLASSIFICATION, r); return r }
        val c = mlClassifier.classify(features)
        val t = start.elapsedNow().inWholeMilliseconds
        val r = if (c.categoryId != ClassificationResult.UNKNOWN_CATEGORY_ID) StageResult(PipelineStage.ML_CLASSIFICATION, c, true, false, null, t) else StageResult.noResult(PipelineStage.ML_CLASSIFICATION, t)
        eventListener?.onStageCompleted(transactionId, PipelineStage.ML_CLASSIFICATION, r)
        return r
    }

    companion object {
        fun createDefault(rules: List<ClassificationRule> = emptyList(), mlClassifier: TransactionClassifier = FallbackClassifier, merchantPriorProvider: MerchantPriorProvider = EmptyMerchantPriorProvider, config: CategorizationConfig = CategorizationConfig.default()) =
            CategorizationPipelineImpl(RuleBasedClassifier(rules), mlClassifier, merchantPriorProvider, config)
    }
}

interface MerchantPriorProvider {
    fun getDistribution(merchantId: String): MerchantCategoryDistribution
    fun updatePrior(merchantId: String, categoryId: String)
}

object EmptyMerchantPriorProvider : MerchantPriorProvider {
    override fun getDistribution(merchantId: String) = MerchantCategoryDistribution.empty(merchantId)
    override fun updatePrior(merchantId: String, categoryId: String) {}
}

data class MerchantCategoryDistribution(val merchantId: String, val probabilities: List<CategoryProbability>, val totalObservations: Int, val effectiveObservations: Float) {
    val mostLikelyCategory get() = probabilities.maxByOrNull { it.probability }
    val isReliable get() = effectiveObservations >= 3f
    companion object { fun empty(merchantId: String) = MerchantCategoryDistribution(merchantId, emptyList(), 0, 0f) }
}

data class CategoryProbability(val categoryId: String, val probability: Float, val observedCount: Int, val decayedWeight: Float)

internal class PipelineStatsCollector {
    private var totalProcessed: Long = 0; private var autoApplied: Long = 0; private var suggested: Long = 0
    private var queuedForReview: Long = 0; private var totalProcessingTimeMs: Long = 0
    private val stageUsageCounts = mutableMapOf<PipelineStage, Long>()
    private var veryLow: Long = 0; private var low: Long = 0; private var medium: Long = 0; private var high: Long = 0; private var veryHigh: Long = 0

    fun recordResult(result: PipelineResult) {
        totalProcessed++; totalProcessingTimeMs += result.processingTimeMs
        when (result.action) { CategorizationAction.AUTO_APPLY -> autoApplied++; CategorizationAction.SUGGEST -> suggested++; CategorizationAction.QUEUE_FOR_REVIEW -> queuedForReview++ }
        result.usedStage?.let { stageUsageCounts[it] = (stageUsageCounts[it] ?: 0) + 1 }
        when { result.confidence < 0.25f -> veryLow++; result.confidence < 0.5f -> low++; result.confidence < 0.75f -> medium++; result.confidence < 0.9f -> high++; else -> veryHigh++ }
    }

    fun getStats() = PipelineStats(totalProcessed, autoApplied, suggested, queuedForReview, if (totalProcessed > 0) totalProcessingTimeMs.toDouble() / totalProcessed else 0.0, stageUsageCounts.toMap(), ConfidenceDistribution(veryLow, low, medium, high, veryHigh))
}
