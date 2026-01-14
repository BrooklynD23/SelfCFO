package com.ledgerlens.categorization

/**
 * A single factor that contributed to a category classification decision.
 *
 * @property reason The type of reason for this factor
 * @property value The specific value that triggered this factor (e.g., merchant name, keyword)
 * @property weight The contribution weight of this factor (0.0 to 1.0)
 * @property rawScore The raw score before normalization (optional)
 * @property metadata Additional context-specific metadata
 */
data class ExplanationFactor(
    val reason: ExplanationReason,
    val value: String,
    val weight: Float,
    val rawScore: Float? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(weight in 0.0f..1.0f) { "Weight must be between 0.0 and 1.0, got $weight" }
        require(rawScore == null || rawScore >= 0.0f) { "Raw score must be non-negative" }
    }

    /**
     * Weight as a percentage (0-100).
     */
    val weightPercent: Int get() = (weight * 100).toInt()

    /**
     * Whether this factor has significant contribution.
     */
    val isSignificant: Boolean get() = weight >= SIGNIFICANCE_THRESHOLD

    companion object {
        const val SIGNIFICANCE_THRESHOLD = 0.1f

        /**
         * Create a merchant match factor.
         */
        fun merchantMatch(
            merchantName: String,
            weight: Float,
            matchType: String = "exact"
        ): ExplanationFactor = ExplanationFactor(
            reason = ExplanationReason.MERCHANT_MATCH,
            value = merchantName,
            weight = weight,
            metadata = mapOf("matchType" to matchType)
        )

        /**
         * Create a keyword match factor.
         */
        fun keywordMatch(
            keyword: String,
            weight: Float,
            position: Int? = null
        ): ExplanationFactor = ExplanationFactor(
            reason = ExplanationReason.KEYWORD_MATCH,
            value = keyword,
            weight = weight,
            metadata = position?.let { mapOf("position" to it.toString()) } ?: emptyMap()
        )

        /**
         * Create an amount pattern factor.
         */
        fun amountPattern(
            pattern: String,
            weight: Float,
            amountRange: String? = null
        ): ExplanationFactor = ExplanationFactor(
            reason = ExplanationReason.AMOUNT_PATTERN,
            value = pattern,
            weight = weight,
            metadata = amountRange?.let { mapOf("range" to it) } ?: emptyMap()
        )

        /**
         * Create a user history factor.
         */
        fun userHistory(
            description: String,
            weight: Float,
            occurrences: Int? = null
        ): ExplanationFactor = ExplanationFactor(
            reason = ExplanationReason.USER_HISTORY,
            value = description,
            weight = weight,
            metadata = occurrences?.let { mapOf("occurrences" to it.toString()) } ?: emptyMap()
        )

        /**
         * Create a rule match factor.
         */
        fun ruleMatch(
            ruleId: String,
            ruleName: String,
            weight: Float = 1.0f
        ): ExplanationFactor = ExplanationFactor(
            reason = ExplanationReason.RULE_MATCH,
            value = ruleName,
            weight = weight,
            metadata = mapOf("ruleId" to ruleId)
        )
    }
}

/**
 * Collection of factors with utility methods.
 */
data class FactorCollection(
    val factors: List<ExplanationFactor>
) {
    /**
     * Total weight of all factors (should sum to approximately 1.0 for normalized factors).
     */
    val totalWeight: Float get() = factors.sumOf { it.weight.toDouble() }.toFloat()

    /**
     * Factors sorted by weight (highest first).
     */
    val sortedByWeight: List<ExplanationFactor>
        get() = factors.sortedByDescending { it.weight }

    /**
     * Only significant factors.
     */
    val significantFactors: List<ExplanationFactor>
        get() = factors.filter { it.isSignificant }

    /**
     * Primary factor (highest weight).
     */
    val primaryFactor: ExplanationFactor?
        get() = factors.maxByOrNull { it.weight }

    /**
     * Factors grouped by reason type.
     */
    val byReason: Map<ExplanationReason, List<ExplanationFactor>>
        get() = factors.groupBy { it.reason }

    /**
     * Normalize weights so they sum to 1.0.
     */
    fun normalize(): FactorCollection {
        if (factors.isEmpty() || totalWeight == 0.0f) return this
        val scale = 1.0f / totalWeight
        return FactorCollection(
            factors.map { it.copy(weight = it.weight * scale) }
        )
    }

    companion object {
        fun of(vararg factors: ExplanationFactor): FactorCollection =
            FactorCollection(factors.toList())

        fun empty(): FactorCollection = FactorCollection(emptyList())
    }
}
