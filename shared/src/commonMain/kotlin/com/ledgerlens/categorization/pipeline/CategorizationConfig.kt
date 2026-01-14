package com.ledgerlens.categorization.pipeline

/**
 * Configuration for the categorization pipeline.
 * Supports runtime updates without restart.
 *
 * @property highConfidenceThreshold Threshold for auto-applying categorization (no review needed)
 * @property reviewThreshold Threshold below which transactions are queued for review
 * @property merchantPriorMinConfidence Minimum confidence from merchant priors to use
 * @property merchantPriorMinObservations Minimum observations for merchant prior to be reliable
 * @property enabledStages Which pipeline stages are enabled
 * @property batchSize Default batch size for batch processing
 * @property maxConcurrentBatches Maximum number of concurrent batch operations
 */
data class CategorizationConfig(
    val highConfidenceThreshold: Float = DEFAULT_HIGH_CONFIDENCE_THRESHOLD,
    val reviewThreshold: Float = DEFAULT_REVIEW_THRESHOLD,
    val merchantPriorMinConfidence: Float = DEFAULT_MERCHANT_PRIOR_MIN_CONFIDENCE,
    val merchantPriorMinObservations: Int = DEFAULT_MERCHANT_PRIOR_MIN_OBSERVATIONS,
    val enabledStages: Set<PipelineStage> = PipelineStage.entries.toSet(),
    val batchSize: Int = DEFAULT_BATCH_SIZE,
    val maxConcurrentBatches: Int = DEFAULT_MAX_CONCURRENT_BATCHES
) {
    init {
        require(highConfidenceThreshold in 0f..1f) { "High confidence threshold must be between 0 and 1" }
        require(reviewThreshold in 0f..1f) { "Review threshold must be between 0 and 1" }
        require(reviewThreshold <= highConfidenceThreshold) { "Review threshold must be <= high confidence threshold" }
        require(merchantPriorMinConfidence in 0f..1f) { "Merchant prior min confidence must be between 0 and 1" }
        require(merchantPriorMinObservations >= 0) { "Merchant prior min observations must be non-negative" }
        require(batchSize > 0) { "Batch size must be positive" }
        require(maxConcurrentBatches > 0) { "Max concurrent batches must be positive" }
    }

    /**
     * Check if a stage is enabled.
     */
    fun isStageEnabled(stage: PipelineStage): Boolean = stage in enabledStages

    /**
     * Determine the action based on confidence level.
     */
    fun determineAction(confidence: Float): CategorizationAction = when {
        confidence >= highConfidenceThreshold -> CategorizationAction.AUTO_APPLY
        confidence >= reviewThreshold -> CategorizationAction.SUGGEST
        else -> CategorizationAction.QUEUE_FOR_REVIEW
    }

    /**
     * Create a copy with updated stage enablement.
     */
    fun withStageEnabled(stage: PipelineStage, enabled: Boolean): CategorizationConfig {
        val newStages = if (enabled) {
            enabledStages + stage
        } else {
            enabledStages - stage
        }
        return copy(enabledStages = newStages)
    }

    /**
     * Create a strict configuration with higher thresholds.
     */
    fun toStrict(): CategorizationConfig = copy(
        highConfidenceThreshold = 0.95f,
        reviewThreshold = 0.7f
    )

    /**
     * Create a lenient configuration with lower thresholds.
     */
    fun toLenient(): CategorizationConfig = copy(
        highConfidenceThreshold = 0.75f,
        reviewThreshold = 0.4f
    )

    companion object {
        const val DEFAULT_HIGH_CONFIDENCE_THRESHOLD = 0.85f
        const val DEFAULT_REVIEW_THRESHOLD = 0.5f
        const val DEFAULT_MERCHANT_PRIOR_MIN_CONFIDENCE = 0.7f
        const val DEFAULT_MERCHANT_PRIOR_MIN_OBSERVATIONS = 3
        const val DEFAULT_BATCH_SIZE = 50
        const val DEFAULT_MAX_CONCURRENT_BATCHES = 4

        /**
         * Default configuration suitable for most use cases.
         */
        fun default(): CategorizationConfig = CategorizationConfig()

        /**
         * Configuration for initial import with more conservative thresholds.
         */
        fun forInitialImport(): CategorizationConfig = CategorizationConfig(
            highConfidenceThreshold = 0.9f,
            reviewThreshold = 0.6f,
            batchSize = 100
        )

        /**
         * Configuration for real-time categorization with faster processing.
         */
        fun forRealTime(): CategorizationConfig = CategorizationConfig(
            highConfidenceThreshold = 0.8f,
            reviewThreshold = 0.5f,
            batchSize = 10
        )
    }
}

/**
 * Pipeline stages in order of execution.
 */
enum class PipelineStage(val order: Int, val description: String) {
    USER_RULES(1, "Apply user-defined categorization rules"),
    MERCHANT_PRIORS(2, "Check merchant category history"),
    ML_CLASSIFICATION(3, "Machine learning classification"),
    EXPLANATION_GENERATION(4, "Generate human-readable explanation"),
    REVIEW_QUEUE(5, "Queue low-confidence results for review");

    companion object {
        /**
         * Get stages in execution order.
         */
        fun inOrder(): List<PipelineStage> = entries.sortedBy { it.order }
    }
}

/**
 * Action to take based on categorization confidence.
 */
enum class CategorizationAction {
    /** Automatically apply the category without user review */
    AUTO_APPLY,

    /** Suggest the category but allow easy one-click change */
    SUGGEST,

    /** Queue for human review before applying */
    QUEUE_FOR_REVIEW
}

/**
 * Mutable configuration holder for runtime updates.
 * Thread-safe wrapper around CategorizationConfig.
 */
class MutableCategorizationConfig(
    initialConfig: CategorizationConfig = CategorizationConfig.default()
) {
    @Volatile
    private var _config: CategorizationConfig = initialConfig

    val config: CategorizationConfig get() = _config

    /**
     * Update the configuration.
     */
    fun update(newConfig: CategorizationConfig) {
        _config = newConfig
    }

    /**
     * Update configuration using a transform function.
     */
    fun update(transform: (CategorizationConfig) -> CategorizationConfig) {
        _config = transform(_config)
    }

    /**
     * Enable or disable a specific stage.
     */
    fun setStageEnabled(stage: PipelineStage, enabled: Boolean) {
        _config = _config.withStageEnabled(stage, enabled)
    }

    /**
     * Reset to default configuration.
     */
    fun reset() {
        _config = CategorizationConfig.default()
    }
}
