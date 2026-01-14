package com.ledgerlens.categorization.pipeline

import com.ledgerlens.categorization.*
import kotlin.time.TimeSource
import kotlin.time.measureTime

/**
 * Implementation of the categorization pipeline.
 * Coordinates multiple classification stages in order:
 * 1. User-defined rules (highest priority)
 * 2. Merchant priors (if confidence > threshold)
 * 3. ML classification
 * 4. Explanation generation
 * 5. Review queue for low-confidence results
 */
class CategorizationPipelineImpl(
    private val ruleClassifier: RuleBasedClassifier,
    private val mlClassifier: TransactionClassifier,
    private val merchantPriorProvider: MerchantPriorProvider,
    private val explanationGenerator: ExplanationGenerator,
    initialConfig: CategorizationConfig = CategorizationConfig.default(),
    private val eventListener: PipelineEventListener? = null
) : CategorizationPipeline {

    private val mutableConfig = MutableCategorizationConfig(initialConfig)
    private val statsCollector = PipelineStatsCollector()

    override val config: CategorizationConfig get() = mutableConfig.config

    override fun updateConfig(newConfig: CategorizationConfig) {
        mutableConfig.update(newConfig)
    }

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

            // Use the best result we have, or unknown
            if (finalResult == null) {
                finalResult = ClassificationResult.unknown()
            }

            // Stage 4: Explanation generation (if enabled and we have a result)
            if (config.isStageEnabled(PipelineStage.EXPLANATION_GENERATION) && 
                finalResult.categoryId != ClassificationResult.UNKNOWN_CATEGORY_ID) {
                val stageResult = executeExplanationStage(finalResult, usedStage, transactionId)
                stageResults[PipelineStage.EXPLANATION_GENERATION] = stageResult
            }

            // Determine action based on confidence
            val action = config.determineAction(finalResult.confidence)

            // Stage 5: Queue for review if needed
            if (config.isStageEnabled(PipelineStage.REVIEW_QUEUE) && 
                action == CategorizationAction.QUEUE_FOR_REVIEW) {
                stageResults[PipelineStage.REVIEW_QUEUE] = StageResult(
                    stage = PipelineStage.REVIEW_QUEUE,
                    classification = null,
                    wasUsed = true,
                    skipped = false,
                    processingTimeMs = 0
                )
            }

            val processingTimeMs = startMark.elapsedNow().inWholeMilliseconds
            val result = PipelineResult(
                transactionId = transactionId,
                classification = finalResult,
                action = action,
                stageResults = stageResults,
                processingTimeMs = processingTimeMs,
                usedStage = usedStage
            )

            statsCollector.recordResult(result)
            eventListener?.onCategorizationComplete(result)

            return result

        } catch (e: Exception) {
            eventListener?.onError(transactionId, usedStage, e)
            val processingTimeMs = startMark.elapsedNow().inWholeMilliseconds
            return PipelineResult.unknown(transactionId, processingTimeMs)
        }
    }

    override fun categorizeBatch(transactions: List<TransactionInput>): List<PipelineResult> {
        return transactions.map { input ->
            categorize(input.features, input.transactionId)
        }
    }

    override fun getStats(): PipelineStats = statsCollector.getStats()

    private fun executeUserRulesStage(
        features: TransactionFeatures, 
        transactionId: String
    ): StageResult {
        eventListener?.onStageStarted(transactionId, PipelineStage.USER_RULES)
        val timeSource = TimeSource.Monotonic
        val startMark = timeSource.markNow()

        val classification = ruleClassifier.classify(features)
        val timeMs = startMark.elapsedNow().inWholeMilliseconds

        val result = if (classification.categoryId != ClassificationResult.UNKNOWN_CATEGORY_ID) {
            StageResult(
                stage = PipelineStage.USER_RULES,
                classification = classification,
                wasUsed = true,
                skipped = false,
                processingTimeMs = timeMs
            )
        } else {
            StageResult.noResult(PipelineStage.USER_RULES, timeMs)
        }

        eventListener?.onStageCompleted(transactionId, PipelineStage.USER_RULES, result)
        return result
    }

    private fun executeMerchantPriorsStage(
        features: TransactionFeatures,
        transactionId: String
    ): StageResult {
        eventListener?.onStageStarted(transactionId, PipelineStage.MERCHANT_PRIORS)
        val timeSource = TimeSource.Monotonic
        val startMark = timeSource.markNow()

        val distribution = merchantPriorProvider.getDistribution(features.merchantNormalized)
        val timeMs = startMark.elapsedNow().inWholeMilliseconds

        if (!distribution.isReliable || distribution.mostLikelyCategory == null) {
            val result = StageResult.noResult(PipelineStage.MERCHANT_PRIORS, timeMs)
            eventListener?.onStageCompleted(transactionId, PipelineStage.MERCHANT_PRIORS, result)
            return result
        }

        val mostLikely = distribution.mostLikelyCategory!!
        if (mostLikely.probability < config.merchantPriorMinConfidence) {
            val result = StageResult(
                stage = PipelineStage.MERCHANT_PRIORS,
                classification = null,
                wasUsed = false,
                skipped = true,
                skipReason = "Confidence ${mostLikely.probability} below threshold ${config.merchantPriorMinConfidence}",
                processingTimeMs = timeMs
            )
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
                reason = "Based on ${distribution.totalObservations} previous transactions from this merchant",
                merchantPrior = mostLikely.probability
            )
        )

        val result = StageResult(
            stage = PipelineStage.MERCHANT_PRIORS,
            classification = classification,
            wasUsed = true,
            skipped = false,
            processingTimeMs = timeMs
        )

        eventListener?.onStageCompleted(transactionId, PipelineStage.MERCHANT_PRIORS, result)
        return result
    }

    private fun executeMLClassificationStage(
        features: TransactionFeatures,
        transactionId: String
    ): StageResult {
        eventListener?.onStageStarted(transactionId, PipelineStage.ML_CLASSIFICATION)
        val timeSource = TimeSource.Monotonic
        val startMark = timeSource.markNow()

        if (!mlClassifier.canClassify(features)) {
            val result = StageResult.skipped(
                PipelineStage.ML_CLASSIFICATION, 
                "ML classifier cannot handle these features"
            )
            eventListener?.onStageCompleted(transactionId, PipelineStage.ML_CLASSIFICATION, result)
            return result
        }

        val classification = mlClassifier.classify(features)
        val timeMs = startMark.elapsedNow().inWholeMilliseconds

        val result = if (classification.categoryId != ClassificationResult.UNKNOWN_CATEGORY_ID) {
            StageResult(
                stage = PipelineStage.ML_CLASSIFICATION,
                classification = classification,
                wasUsed = true,
                skipped = false,
                processingTimeMs = timeMs
            )
        } else {
            StageResult.noResult(PipelineStage.ML_CLASSIFICATION, timeMs)
        }

        eventListener?.onStageCompleted(transactionId, PipelineStage.ML_CLASSIFICATION, result)
        return result
    }

    private fun executeExplanationStage(
        classification: ClassificationResult,
        usedStage: PipelineStage?,
        transactionId: String
    ): StageResult {
        eventListener?.onStageStarted(transactionId, PipelineStage.EXPLANATION_GENERATION)
        val timeSource = TimeSource.Monotonic
        val startMark = timeSource.markNow()

        // The classification already has an explanation from the classifier
        // This stage could enhance it, but for now we just mark it as processed
        val timeMs = startMark.elapsedNow().inWholeMilliseconds

        val result = StageResult(
            stage = PipelineStage.EXPLANATION_GENERATION,
            classification = classification,
            wasUsed = true,
            skipped = false,
            processingTimeMs = timeMs
        )

        eventListener?.onStageCompleted(transactionId, PipelineStage.EXPLANATION_GENERATION, result)
        return result
    }

    companion object {
        /**
         * Create a pipeline with default components.
         */
        fun createDefault(
            rules: List<ClassificationRule> = RuleBasedClassifier.defaultRules(),
            model: NaiveBayesModel = NaiveBayesModel(),
            merchantPriorProvider: MerchantPriorProvider = EmptyMerchantPriorProvider,
            config: CategorizationConfig = CategorizationConfig.default()
        ): CategorizationPipelineImpl {
            return CategorizationPipelineImpl(
                ruleClassifier = RuleBasedClassifier(rules),
                mlClassifier = NaiveBayesClassifier(model),
                merchantPriorProvider = merchantPriorProvider,
                explanationGenerator = ExplanationGenerator(),
                initialConfig = config
            )
        }
    }
}

/**
 * Provider interface for merchant prior distributions.
 */
interface MerchantPriorProvider {
    fun getDistribution(merchantId: String): MerchantCategoryDistribution
    fun updatePrior(merchantId: String, categoryId: String)
}

/**
 * Empty implementation when no merchant history is available.
 */
object EmptyMerchantPriorProvider : MerchantPriorProvider {
    override fun getDistribution(merchantId: String): MerchantCategoryDistribution {
        return MerchantCategoryDistribution.empty(merchantId)
    }

    override fun updatePrior(merchantId: String, categoryId: String) {
        // No-op
    }
}

/**
 * Collects statistics about pipeline execution.
 */
internal class PipelineStatsCollector {
    private var totalProcessed: Long = 0
    private var autoApplied: Long = 0
    private var suggested: Long = 0
    private var queuedForReview: Long = 0
    private var totalProcessingTimeMs: Long = 0
    private val stageUsageCounts = mutableMapOf<PipelineStage, Long>()
    private var veryLowConfidence: Long = 0
    private var lowConfidence: Long = 0
    private var mediumConfidence: Long = 0
    private var highConfidence: Long = 0
    private var veryHighConfidence: Long = 0

    fun recordResult(result: PipelineResult) {
        totalProcessed++
        totalProcessingTimeMs += result.processingTimeMs

        when (result.action) {
            CategorizationAction.AUTO_APPLY -> autoApplied++
            CategorizationAction.SUGGEST -> suggested++
            CategorizationAction.QUEUE_FOR_REVIEW -> queuedForReview++
        }

        result.usedStage?.let { stage ->
            stageUsageCounts[stage] = (stageUsageCounts[stage] ?: 0) + 1
        }

        when {
            result.confidence < 0.25f -> veryLowConfidence++
            result.confidence < 0.5f -> lowConfidence++
            result.confidence < 0.75f -> mediumConfidence++
            result.confidence < 0.9f -> highConfidence++
            else -> veryHighConfidence++
        }
    }

    fun getStats(): PipelineStats = PipelineStats(
        totalProcessed = totalProcessed,
        autoApplied = autoApplied,
        suggested = suggested,
        queuedForReview = queuedForReview,
        averageProcessingTimeMs = if (totalProcessed > 0) {
            totalProcessingTimeMs.toDouble() / totalProcessed
        } else 0.0,
        stageUsageCounts = stageUsageCounts.toMap(),
        confidenceDistribution = ConfidenceDistribution(
            veryLow = veryLowConfidence,
            low = lowConfidence,
            medium = mediumConfidence,
            high = highConfidence,
            veryHigh = veryHighConfidence
        )
    )
}
