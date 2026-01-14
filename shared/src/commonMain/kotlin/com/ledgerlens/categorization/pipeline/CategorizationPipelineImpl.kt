package com.ledgerlens.categorization.pipeline

import com.ledgerlens.categorization.MerchantPriorProvider
import com.ledgerlens.categorization.MerchantCategoryDistribution
import kotlin.time.TimeSource

/**
 * Implementation of the categorization pipeline with staged processing.
 */
class CategorizationPipelineImpl(
    private val ruleClassifier: RuleBasedClassifier,
    private val mlClassifier: TransactionClassifier,
    private val merchantPriorProvider: MerchantPriorProvider,
    initialConfig: CategorizationConfig = CategorizationConfig.default(),
    private val eventListener: PipelineEventListener? = null
) : CategorizationPipeline {

    private val mutableConfig = MutableCategorizationConfig(initialConfig)
    private val statsCollector = PipelineStatsCollector()

    override val config: CategorizationConfig get() = mutableConfig.config
    override fun updateConfig(newConfig: CategorizationConfig) { mutableConfig.update(newConfig) }

    override fun categorize(features: TransactionFeatures, transactionId: String): PipelineResult {
        val timeSource = TimeSource.Monotonic
        val startMark = timeSource.markNow()
        val stageResults = mutableMapOf<PipelineStage, StageResult>()
        var finalResult: ClassificationResult? = null
        var usedStage: PipelineStage? = null

        try {
            // Stage 1: User-defined rules
            if (config.isStageEnabled(PipelineStage.USER_RULES)) {
                val stageResult = executeUserRulesStage(features, transactionId)
                stageResults[PipelineStage.USER_RULES] = stageResult
                if (stageResult.hasResult && stageResult.classification!!.isHighConfidence) {
                    finalResult = stageResult.classification
                    usedStage = PipelineStage.USER_RULES
                }
            }

            // Stage 2: Merchant priors
            if (finalResult == null && config.isStageEnabled(PipelineStage.MERCHANT_PRIORS)) {
                val stageResult = executeMerchantPriorsStage(features, transactionId)
                stageResults[PipelineStage.MERCHANT_PRIORS] = stageResult
                if (stageResult.hasResult && 
                    stageResult.classification!!.confidence >= config.merchantPriorMinConfidence) {
                    finalResult = stageResult.classification
                    usedStage = PipelineStage.MERCHANT_PRIORS
                }
            }

            // Stage 3: ML classification
            if (finalResult == null && config.isStageEnabled(PipelineStage.ML_CLASSIFICATION)) {
                val stageResult = executeMLClassificationStage(features, transactionId)
                stageResults[PipelineStage.ML_CLASSIFICATION] = stageResult
                if (stageResult.hasResult) {
                    finalResult = stageResult.classification
                    usedStage = PipelineStage.ML_CLASSIFICATION
                }
            }

            if (finalResult == null) finalResult = ClassificationResult.unknown()

            val action = config.determineAction(finalResult.confidence)

            if (config.isStageEnabled(PipelineStage.REVIEW_QUEUE) && 
                action == CategorizationAction.QUEUE_FOR_REVIEW) {
                stageResults[PipelineStage.REVIEW_QUEUE] = StageResult(
                    PipelineStage.REVIEW_QUEUE, null, true, false, null, 0
                )
            }

            val processingTimeMs = startMark.elapsedNow().inWholeMilliseconds
            val result = PipelineResult(transactionId, finalResult, action, stageResults, processingTimeMs, usedStage)

            statsCollector.recordResult(result)
            eventListener?.onCategorizationComplete(result)
            return result

        } catch (e: Exception) {
            eventListener?.onError(transactionId, usedStage, e)
            return PipelineResult.unknown(transactionId, startMark.elapsedNow().inWholeMilliseconds)
        }
    }

    override fun categorizeBatch(transactions: List<TransactionInput>): List<PipelineResult> {
        return transactions.map { categorize(it.features, it.transactionId) }
    }

    override fun getStats(): PipelineStats = statsCollector.getStats()

    private fun executeUserRulesStage(features: TransactionFeatures, transactionId: String): StageResult {
        eventListener?.onStageStarted(transactionId, PipelineStage.USER_RULES)
        val startMark = TimeSource.Monotonic.markNow()
        val classification = ruleClassifier.classify(features)
        val timeMs = startMark.elapsedNow().inWholeMilliseconds

        val result = if (classification.categoryId != ClassificationResult.UNKNOWN_CATEGORY_ID) {
            StageResult(PipelineStage.USER_RULES, classification, true, false, null, timeMs)
        } else {
            StageResult.noResult(PipelineStage.USER_RULES, timeMs)
        }
        eventListener?.onStageCompleted(transactionId, PipelineStage.USER_RULES, result)
        return result
    }

    private fun executeMerchantPriorsStage(features: TransactionFeatures, transactionId: String): StageResult {
        eventListener?.onStageStarted(transactionId, PipelineStage.MERCHANT_PRIORS)
        val startMark = TimeSource.Monotonic.markNow()
        val distribution = merchantPriorProvider.getDistribution(features.merchantNormalized)
        val timeMs = startMark.elapsedNow().inWholeMilliseconds

        if (!distribution.isReliable || distribution.mostLikelyCategory == null) {
            val result = StageResult.noResult(PipelineStage.MERCHANT_PRIORS, timeMs)
            eventListener?.onStageCompleted(transactionId, PipelineStage.MERCHANT_PRIORS, result)
            return result
        }

        val mostLikely = distribution.mostLikelyCategory!!
        if (mostLikely.probability < config.merchantPriorMinConfidence) {
            val result = StageResult(PipelineStage.MERCHANT_PRIORS, null, false, true,
                "Confidence ${mostLikely.probability} below threshold", timeMs)
            eventListener?.onStageCompleted(transactionId, PipelineStage.MERCHANT_PRIORS, result)
            return result
        }

        val classification = ClassificationResult(
            categoryId = mostLikely.categoryId,
            confidence = mostLikely.probability,
            alternatives = distribution.probabilities
                .filter { it.categoryId != mostLikely.categoryId }
                .take(3)
                .map { CategoryScore(it.categoryId, it.probability) },
            explanation = ClassificationExplanation(
                classifierUsed = "merchant-prior",
                reason = "Based on ${distribution.totalObservations} previous transactions",
                merchantPrior = mostLikely.probability
            )
        )

        val result = StageResult(PipelineStage.MERCHANT_PRIORS, classification, true, false, null, timeMs)
        eventListener?.onStageCompleted(transactionId, PipelineStage.MERCHANT_PRIORS, result)
        return result
    }

    private fun executeMLClassificationStage(features: TransactionFeatures, transactionId: String): StageResult {
        eventListener?.onStageStarted(transactionId, PipelineStage.ML_CLASSIFICATION)
        val startMark = TimeSource.Monotonic.markNow()

        if (!mlClassifier.canClassify(features)) {
            val result = StageResult.skipped(PipelineStage.ML_CLASSIFICATION, "ML classifier cannot handle features")
            eventListener?.onStageCompleted(transactionId, PipelineStage.ML_CLASSIFICATION, result)
            return result
        }

        val classification = mlClassifier.classify(features)
        val timeMs = startMark.elapsedNow().inWholeMilliseconds

        val result = if (classification.categoryId != ClassificationResult.UNKNOWN_CATEGORY_ID) {
            StageResult(PipelineStage.ML_CLASSIFICATION, classification, true, false, null, timeMs)
        } else {
            StageResult.noResult(PipelineStage.ML_CLASSIFICATION, timeMs)
        }
        eventListener?.onStageCompleted(transactionId, PipelineStage.ML_CLASSIFICATION, result)
        return result
    }

    companion object {
        fun createDefault(
            rules: List<ClassificationRule> = emptyList(),
            mlClassifier: TransactionClassifier = FallbackClassifier,
            merchantPriorProvider: MerchantPriorProvider = EmptyMerchantPriorProvider,
            config: CategorizationConfig = CategorizationConfig.default()
        ): CategorizationPipelineImpl = CategorizationPipelineImpl(
            RuleBasedClassifier(rules), mlClassifier, merchantPriorProvider, config
        )
    }
}

object EmptyMerchantPriorProvider : MerchantPriorProvider {
    override fun getDistribution(merchantId: String) = MerchantCategoryDistribution.empty(merchantId)
    override fun updatePrior(merchantId: String, categoryId: String) {}
}

internal class PipelineStatsCollector {
    private var totalProcessed: Long = 0
    private var autoApplied: Long = 0
    private var suggested: Long = 0
    private var queuedForReview: Long = 0
    private var totalProcessingTimeMs: Long = 0
    private val stageUsageCounts = mutableMapOf<PipelineStage, Long>()
    private var veryLow: Long = 0; private var low: Long = 0; private var medium: Long = 0
    private var high: Long = 0; private var veryHigh: Long = 0

    fun recordResult(result: PipelineResult) {
        totalProcessed++
        totalProcessingTimeMs += result.processingTimeMs
        when (result.action) {
            CategorizationAction.AUTO_APPLY -> autoApplied++
            CategorizationAction.SUGGEST -> suggested++
            CategorizationAction.QUEUE_FOR_REVIEW -> queuedForReview++
        }
        result.usedStage?.let { stageUsageCounts[it] = (stageUsageCounts[it] ?: 0) + 1 }
        when {
            result.confidence < 0.25f -> veryLow++
            result.confidence < 0.5f -> low++
            result.confidence < 0.75f -> medium++
            result.confidence < 0.9f -> high++
            else -> veryHigh++
        }
    }

    fun getStats() = PipelineStats(
        totalProcessed, autoApplied, suggested, queuedForReview,
        if (totalProcessed > 0) totalProcessingTimeMs.toDouble() / totalProcessed else 0.0,
        stageUsageCounts.toMap(),
        ConfidenceDistribution(veryLow, low, medium, high, veryHigh)
    )
}
