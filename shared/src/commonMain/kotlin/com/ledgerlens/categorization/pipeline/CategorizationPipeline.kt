package com.ledgerlens.categorization.pipeline

import com.ledgerlens.categorization.ClassificationResult
import com.ledgerlens.categorization.TransactionFeatures

/**
 * Main interface for the categorization pipeline.
 * Orchestrates multiple classification stages to categorize transactions.
 */
interface CategorizationPipeline {

    /**
     * Categorize a single transaction.
     *
     * @param features Extracted features from the transaction
     * @param transactionId Unique identifier for the transaction
     * @return Complete pipeline result including category, confidence, and metadata
     */
    fun categorize(features: TransactionFeatures, transactionId: String): PipelineResult

    /**
     * Categorize multiple transactions in a batch.
     *
     * @param transactions List of transaction features with their IDs
     * @return List of pipeline results in the same order as input
     */
    fun categorizeBatch(transactions: List<TransactionInput>): List<PipelineResult>

    /**
     * Get the current pipeline configuration.
     */
    val config: CategorizationConfig

    /**
     * Update pipeline configuration at runtime.
     */
    fun updateConfig(newConfig: CategorizationConfig)

    /**
     * Get statistics about pipeline performance.
     */
    fun getStats(): PipelineStats
}

/**
 * Input for categorization containing transaction features and metadata.
 */
data class TransactionInput(
    val transactionId: String,
    val features: TransactionFeatures,
    val existingCategoryId: String? = null,
    val accountId: String? = null
)

/**
 * Result of processing a transaction through the pipeline.
 */
data class PipelineResult(
    val transactionId: String,
    val classification: ClassificationResult,
    val action: CategorizationAction,
    val stageResults: Map<PipelineStage, StageResult>,
    val processingTimeMs: Long,
    val usedStage: PipelineStage?
) {
    /**
     * The final category ID from classification.
     */
    val categoryId: String get() = classification.categoryId

    /**
     * The final confidence score.
     */
    val confidence: Float get() = classification.confidence

    /**
     * Whether this result needs human review.
     */
    val needsReview: Boolean get() = action == CategorizationAction.QUEUE_FOR_REVIEW

    /**
     * Whether this result was auto-applied.
     */
    val wasAutoApplied: Boolean get() = action == CategorizationAction.AUTO_APPLY

    /**
     * Get result from a specific stage.
     */
    fun getStageResult(stage: PipelineStage): StageResult? = stageResults[stage]

    companion object {
        /**
         * Create a result when no categorization was possible.
         */
        fun unknown(transactionId: String, processingTimeMs: Long): PipelineResult = PipelineResult(
            transactionId = transactionId,
            classification = ClassificationResult.unknown(),
            action = CategorizationAction.QUEUE_FOR_REVIEW,
            stageResults = emptyMap(),
            processingTimeMs = processingTimeMs,
            usedStage = null
        )
    }
}

/**
 * Result from a single pipeline stage.
 */
data class StageResult(
    val stage: PipelineStage,
    val classification: ClassificationResult?,
    val wasUsed: Boolean,
    val skipped: Boolean,
    val skipReason: String? = null,
    val processingTimeMs: Long
) {
    /**
     * Whether this stage produced a usable result.
     */
    val hasResult: Boolean get() = classification != null && 
        classification.categoryId != ClassificationResult.UNKNOWN_CATEGORY_ID

    companion object {
        fun skipped(stage: PipelineStage, reason: String): StageResult = StageResult(
            stage = stage,
            classification = null,
            wasUsed = false,
            skipped = true,
            skipReason = reason,
            processingTimeMs = 0
        )

        fun noResult(stage: PipelineStage, timeMs: Long): StageResult = StageResult(
            stage = stage,
            classification = null,
            wasUsed = false,
            skipped = false,
            processingTimeMs = timeMs
        )
    }
}

/**
 * Statistics about pipeline performance and usage.
 */
data class PipelineStats(
    val totalProcessed: Long,
    val autoApplied: Long,
    val suggested: Long,
    val queuedForReview: Long,
    val averageProcessingTimeMs: Double,
    val stageUsageCounts: Map<PipelineStage, Long>,
    val confidenceDistribution: ConfidenceDistribution
) {
    val autoApplyRate: Float get() = if (totalProcessed > 0) autoApplied.toFloat() / totalProcessed else 0f
    val reviewRate: Float get() = if (totalProcessed > 0) queuedForReview.toFloat() / totalProcessed else 0f

    companion object {
        fun empty(): PipelineStats = PipelineStats(
            totalProcessed = 0,
            autoApplied = 0,
            suggested = 0,
            queuedForReview = 0,
            averageProcessingTimeMs = 0.0,
            stageUsageCounts = emptyMap(),
            confidenceDistribution = ConfidenceDistribution.empty()
        )
    }
}

/**
 * Distribution of confidence scores across buckets.
 */
data class ConfidenceDistribution(
    val veryLow: Long,    // 0.0 - 0.25
    val low: Long,        // 0.25 - 0.5
    val medium: Long,     // 0.5 - 0.75
    val high: Long,       // 0.75 - 0.9
    val veryHigh: Long    // 0.9 - 1.0
) {
    val total: Long get() = veryLow + low + medium + high + veryHigh

    fun bucket(confidence: Float): ConfidenceBucket = when {
        confidence < 0.25f -> ConfidenceBucket.VERY_LOW
        confidence < 0.5f -> ConfidenceBucket.LOW
        confidence < 0.75f -> ConfidenceBucket.MEDIUM
        confidence < 0.9f -> ConfidenceBucket.HIGH
        else -> ConfidenceBucket.VERY_HIGH
    }

    companion object {
        fun empty(): ConfidenceDistribution = ConfidenceDistribution(0, 0, 0, 0, 0)
    }
}

enum class ConfidenceBucket {
    VERY_LOW, LOW, MEDIUM, HIGH, VERY_HIGH
}

/**
 * Listener for pipeline events.
 */
interface PipelineEventListener {
    fun onStageStarted(transactionId: String, stage: PipelineStage) {}
    fun onStageCompleted(transactionId: String, stage: PipelineStage, result: StageResult) {}
    fun onCategorizationComplete(result: PipelineResult) {}
    fun onError(transactionId: String, stage: PipelineStage?, error: Throwable) {}
}
