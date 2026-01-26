package com.ledgerlens.categorization.pipeline

data class CategorizationConfig(
    val highConfidenceThreshold: Float = 0.85f,
    val reviewThreshold: Float = 0.5f,
    val merchantPriorMinConfidence: Float = 0.7f,
    val merchantPriorMinObservations: Int = 3,
    val enabledStages: Set<PipelineStage> = PipelineStage.entries.toSet(),
    val batchSize: Int = 50,
    val maxConcurrentBatches: Int = 4
) {
    init {
        require(highConfidenceThreshold in 0f..1f)
        require(reviewThreshold in 0f..1f)
        require(reviewThreshold <= highConfidenceThreshold)
        require(merchantPriorMinConfidence in 0f..1f)
        require(merchantPriorMinObservations >= 0)
        require(batchSize > 0)
        require(maxConcurrentBatches > 0)
    }

    fun isStageEnabled(stage: PipelineStage) = stage in enabledStages
    fun determineAction(confidence: Float) = when {
        confidence >= highConfidenceThreshold -> CategorizationAction.AUTO_APPLY
        confidence >= reviewThreshold -> CategorizationAction.SUGGEST
        else -> CategorizationAction.QUEUE_FOR_REVIEW
    }
    fun withStageEnabled(stage: PipelineStage, enabled: Boolean) =
        copy(enabledStages = if (enabled) enabledStages + stage else enabledStages - stage)
    fun toStrict() = copy(highConfidenceThreshold = 0.95f, reviewThreshold = 0.7f)
    fun toLenient() = copy(highConfidenceThreshold = 0.75f, reviewThreshold = 0.4f)

    companion object {
        fun default() = CategorizationConfig()
        fun forInitialImport() =
            CategorizationConfig(highConfidenceThreshold = 0.9f, reviewThreshold = 0.6f, batchSize = 100)
        fun forRealTime() = CategorizationConfig(highConfidenceThreshold = 0.8f, reviewThreshold = 0.5f, batchSize = 10)
    }
}

enum class PipelineStage(val order: Int, val description: String) {
    USER_RULES(1, "Apply user-defined categorization rules"),
    MERCHANT_PRIORS(2, "Check merchant category history"),
    ML_CLASSIFICATION(3, "Machine learning classification"),
    EXPLANATION_GENERATION(4, "Generate human-readable explanation"),
    REVIEW_QUEUE(5, "Queue low-confidence results for review");

    companion object {
        fun inOrder() = entries.sortedBy { it.order }
    }
}

enum class CategorizationAction { AUTO_APPLY, SUGGEST, QUEUE_FOR_REVIEW }

class MutableCategorizationConfig(initialConfig: CategorizationConfig = CategorizationConfig.default()) {
    @Volatile private var _config = initialConfig
    val config get() = _config
    fun update(newConfig: CategorizationConfig) {
        _config = newConfig
    }
    fun update(transform: (CategorizationConfig) -> CategorizationConfig) {
        _config = transform(_config)
    }
    fun setStageEnabled(stage: PipelineStage, enabled: Boolean) {
        _config = _config.withStageEnabled(stage, enabled)
    }
    fun reset() {
        _config = CategorizationConfig.default()
    }
}
