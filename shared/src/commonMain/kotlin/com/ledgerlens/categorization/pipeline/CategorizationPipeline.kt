package com.ledgerlens.categorization.pipeline

interface CategorizationPipeline {
    fun categorize(features: TransactionFeatures, transactionId: String): PipelineResult
    fun categorizeBatch(transactions: List<TransactionInput>): List<PipelineResult>
    val config: CategorizationConfig
    fun updateConfig(newConfig: CategorizationConfig)
    fun getStats(): PipelineStats
}

data class TransactionInput(val transactionId: String, val features: TransactionFeatures, val existingCategoryId: String? = null, val accountId: String? = null)

data class TransactionFeatures(
    val merchantNormalized: String, val descriptionRaw: String, val descriptionTokens: List<String>,
    val amountCents: Long, val amountBucket: AmountBucket, val isDebit: Boolean, val dayOfWeek: Int, val dayOfMonth: Int, val accountId: String? = null
)

enum class AmountBucket {
    MICRO, SMALL, MEDIUM, LARGE, VERY_LARGE, HUGE;
    companion object {
        fun fromCents(amountCents: Long): AmountBucket {
            val abs = kotlin.math.abs(amountCents)
            return when { abs < 500 -> MICRO; abs < 2500 -> SMALL; abs < 10000 -> MEDIUM; abs < 50000 -> LARGE; abs < 200000 -> VERY_LARGE; else -> HUGE }
        }
    }
}

data class PipelineResult(
    val transactionId: String, val classification: ClassificationResult, val action: CategorizationAction,
    val stageResults: Map<PipelineStage, StageResult>, val processingTimeMs: Long, val usedStage: PipelineStage?
) {
    val categoryId get() = classification.categoryId
    val confidence get() = classification.confidence
    val needsReview get() = action == CategorizationAction.QUEUE_FOR_REVIEW
    val wasAutoApplied get() = action == CategorizationAction.AUTO_APPLY
    fun getStageResult(stage: PipelineStage) = stageResults[stage]
    companion object {
        fun unknown(transactionId: String, processingTimeMs: Long) = PipelineResult(transactionId, ClassificationResult.unknown(), CategorizationAction.QUEUE_FOR_REVIEW, emptyMap(), processingTimeMs, null)
    }
}

data class ClassificationResult(val categoryId: String, val confidence: Float, val alternatives: List<CategoryScore> = emptyList(), val explanation: ClassificationExplanation) {
    val isHighConfidence get() = confidence >= 0.85f
    val needsReview get() = confidence < 0.5f
    companion object {
        const val UNKNOWN_CATEGORY_ID = "unknown"
        fun unknown() = ClassificationResult(UNKNOWN_CATEGORY_ID, 0f, emptyList(), ClassificationExplanation("none", "No classifier could determine a category"))
    }
}

data class CategoryScore(val categoryId: String, val score: Float)
data class ClassificationExplanation(val classifierUsed: String, val reason: String, val ruleMatched: String? = null, val merchantPrior: Float? = null, val tokenMatches: List<String> = emptyList(), val amountPattern: String? = null)

data class StageResult(val stage: PipelineStage, val classification: ClassificationResult?, val wasUsed: Boolean, val skipped: Boolean, val skipReason: String? = null, val processingTimeMs: Long) {
    val hasResult get() = classification != null && classification.categoryId != ClassificationResult.UNKNOWN_CATEGORY_ID
    companion object {
        fun skipped(stage: PipelineStage, reason: String) = StageResult(stage, null, false, true, reason, 0)
        fun noResult(stage: PipelineStage, timeMs: Long) = StageResult(stage, null, false, false, null, timeMs)
    }
}

data class PipelineStats(val totalProcessed: Long, val autoApplied: Long, val suggested: Long, val queuedForReview: Long, val averageProcessingTimeMs: Double, val stageUsageCounts: Map<PipelineStage, Long>, val confidenceDistribution: ConfidenceDistribution) {
    val autoApplyRate get() = if (totalProcessed > 0) autoApplied.toFloat() / totalProcessed else 0f
    val reviewRate get() = if (totalProcessed > 0) queuedForReview.toFloat() / totalProcessed else 0f
    companion object { fun empty() = PipelineStats(0, 0, 0, 0, 0.0, emptyMap(), ConfidenceDistribution.empty()) }
}

data class ConfidenceDistribution(val veryLow: Long, val low: Long, val medium: Long, val high: Long, val veryHigh: Long) {
    val total get() = veryLow + low + medium + high + veryHigh
    companion object { fun empty() = ConfidenceDistribution(0, 0, 0, 0, 0) }
}

interface PipelineEventListener {
    fun onStageStarted(transactionId: String, stage: PipelineStage) {}
    fun onStageCompleted(transactionId: String, stage: PipelineStage, result: StageResult) {}
    fun onCategorizationComplete(result: PipelineResult) {}
    fun onError(transactionId: String, stage: PipelineStage?, error: Throwable) {}
}
