package com.ledgerlens.categorization

/**
 * Comprehensive explanation of why a category was assigned to a transaction.
 * Designed for transparency and user understanding of categorization decisions.
 *
 * @property primaryReason The main reason for the categorization
 * @property factors Individual factors that contributed to the decision
 * @property overallConfidence The combined confidence score (0.0 to 1.0)
 * @property classifierSource Which classifier produced this result
 * @property humanReadableSummary Pre-generated human-readable summary
 */
data class CategoryExplanation(
    val primaryReason: ExplanationReason,
    val factors: FactorCollection,
    val overallConfidence: Float,
    val classifierSource: String,
    val humanReadableSummary: String? = null
) {
    init {
        require(overallConfidence in 0.0f..1.0f) {
            "Overall confidence must be between 0.0 and 1.0, got $overallConfidence"
        }
    }

    /**
     * Confidence breakdown by factor.
     */
    val confidenceBreakdown: Map<ExplanationReason, Float>
        get() = factors.byReason.mapValues { (_, factorList) ->
            factorList.sumOf { it.weight.toDouble() }.toFloat()
        }

    /**
     * Whether the explanation has multiple contributing factors.
     */
    val hasMultipleFactors: Boolean
        get() = factors.significantFactors.size > 1

    /**
     * Top N contributing factors.
     */
    fun topFactors(n: Int = 3): List<ExplanationFactor> =
        factors.sortedByWeight.take(n)

    /**
     * Whether this is a high-confidence classification.
     */
    val isHighConfidence: Boolean
        get() = overallConfidence >= HIGH_CONFIDENCE_THRESHOLD

    /**
     * Whether this classification needs user review.
     */
    val needsReview: Boolean
        get() = overallConfidence < REVIEW_THRESHOLD

    /**
     * Confidence level as a descriptive enum.
     */
    val confidenceLevel: ConfidenceLevel
        get() = ConfidenceLevel.fromScore(overallConfidence)

    companion object {
        const val HIGH_CONFIDENCE_THRESHOLD = 0.85f
        const val REVIEW_THRESHOLD = 0.5f

        /**
         * Create an unknown/fallback explanation.
         */
        fun unknown(classifierSource: String = "none"): CategoryExplanation =
            CategoryExplanation(
                primaryReason = ExplanationReason.DEFAULT_FALLBACK,
                factors = FactorCollection.empty(),
                overallConfidence = 0.0f,
                classifierSource = classifierSource,
                humanReadableSummary = "No category could be determined"
            )

        /**
         * Create a simple single-factor explanation.
         */
        fun singleFactor(
            reason: ExplanationReason,
            value: String,
            confidence: Float,
            classifierSource: String
        ): CategoryExplanation = CategoryExplanation(
            primaryReason = reason,
            factors = FactorCollection.of(
                ExplanationFactor(reason, value, 1.0f)
            ),
            overallConfidence = confidence,
            classifierSource = classifierSource
        )

        /**
         * Create explanation from a rule match.
         */
        fun fromRule(
            ruleId: String,
            ruleName: String,
            classifierSource: String = "rule_engine"
        ): CategoryExplanation = CategoryExplanation(
            primaryReason = ExplanationReason.RULE_MATCH,
            factors = FactorCollection.of(
                ExplanationFactor.ruleMatch(ruleId, ruleName)
            ),
            overallConfidence = 1.0f,
            classifierSource = classifierSource,
            humanReadableSummary = "Matched rule: $ruleName"
        )
    }
}

/**
 * Descriptive confidence levels for UI display.
 */
enum class ConfidenceLevel(
    val localizationKey: String,
    val minScore: Float
) {
    VERY_HIGH("confidence.very_high", 0.95f),
    HIGH("confidence.high", 0.85f),
    MEDIUM("confidence.medium", 0.65f),
    LOW("confidence.low", 0.5f),
    VERY_LOW("confidence.very_low", 0.0f);

    companion object {
        fun fromScore(score: Float): ConfidenceLevel = when {
            score >= VERY_HIGH.minScore -> VERY_HIGH
            score >= HIGH.minScore -> HIGH
            score >= MEDIUM.minScore -> MEDIUM
            score >= LOW.minScore -> LOW
            else -> VERY_LOW
        }

        fun defaultDescription(level: ConfidenceLevel): String = when (level) {
            VERY_HIGH -> "Very confident"
            HIGH -> "Confident"
            MEDIUM -> "Moderately confident"
            LOW -> "Low confidence"
            VERY_LOW -> "Very low confidence"
        }
    }
}
