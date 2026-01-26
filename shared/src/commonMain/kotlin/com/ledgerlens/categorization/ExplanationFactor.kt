package com.ledgerlens.categorization

/**
 * Represents a single factor contributing to a category classification.
 */
data class ExplanationFactor(
    val reason: ExplanationReason,
    val value: String,
    val weight: Float,
    val rawScore: Float,
    val metadata: Map<String, Any> = emptyMap()
) {
    /**
     * Normalized contribution of this factor (0.0 to 1.0).
     */
    val normalizedContribution: Float
        get() = (weight * rawScore).coerceIn(0f, 1f)

    /**
     * Human-readable description of the factor.
     */
    fun describe(): String = when (reason) {
        ExplanationReason.MERCHANT_MATCH -> "Merchant '$value'"
        ExplanationReason.KEYWORD_MATCH -> "Keyword '$value'"
        ExplanationReason.RULE_MATCH -> "Rule: $value"
        ExplanationReason.USER_HISTORY -> "Previous: $value"
        ExplanationReason.AMOUNT_PATTERN -> "Amount: $value"
        ExplanationReason.TEMPORAL_PATTERN -> "Time: $value"
        ExplanationReason.ACCOUNT_CONTEXT -> "Account: $value"
        ExplanationReason.COMBINED_FACTORS -> "Combined: $value"
        ExplanationReason.DEFAULT_FALLBACK -> "Default"
    }

    companion object {
        fun merchantMatch(merchantName: String, score: Float) = ExplanationFactor(
            reason = ExplanationReason.MERCHANT_MATCH,
            value = merchantName,
            weight = ExplanationReason.MERCHANT_MATCH.weight,
            rawScore = score
        )

        fun keywordMatch(keyword: String, score: Float) = ExplanationFactor(
            reason = ExplanationReason.KEYWORD_MATCH,
            value = keyword,
            weight = ExplanationReason.KEYWORD_MATCH.weight,
            rawScore = score
        )

        fun ruleMatch(ruleName: String, score: Float = 1.0f) = ExplanationFactor(
            reason = ExplanationReason.RULE_MATCH,
            value = ruleName,
            weight = ExplanationReason.RULE_MATCH.weight,
            rawScore = score
        )

        fun userHistory(description: String, score: Float) = ExplanationFactor(
            reason = ExplanationReason.USER_HISTORY,
            value = description,
            weight = ExplanationReason.USER_HISTORY.weight,
            rawScore = score
        )

        fun amountPattern(bucket: String, score: Float) = ExplanationFactor(
            reason = ExplanationReason.AMOUNT_PATTERN,
            value = bucket,
            weight = ExplanationReason.AMOUNT_PATTERN.weight,
            rawScore = score
        )
    }
}

/**
 * Collection of factors with normalization and aggregation utilities.
 */
class FactorCollection(
    factors: List<ExplanationFactor> = emptyList()
) {
    private val _factors = factors.toMutableList()

    val factors: List<ExplanationFactor> get() = _factors.toList()
    val size: Int get() = _factors.size
    val isEmpty: Boolean get() = _factors.isEmpty()

    /**
     * Total weighted score of all factors.
     */
    val totalWeightedScore: Float
        get() = _factors.sumOf { (it.weight * it.rawScore).toDouble() }.toFloat()

    /**
     * Sum of all weights.
     */
    val totalWeight: Float
        get() = _factors.sumOf { it.weight.toDouble() }.toFloat()

    /**
     * Get factors sorted by contribution (highest first).
     */
    val sortedByContribution: List<ExplanationFactor>
        get() = _factors.sortedByDescending { it.normalizedContribution }

    /**
     * Get the primary (most influential) factor.
     */
    val primaryFactor: ExplanationFactor?
        get() = _factors.maxByOrNull { it.normalizedContribution }

    /**
     * Add a factor to the collection.
     */
    fun add(factor: ExplanationFactor) {
        _factors.add(factor)
    }

    /**
     * Get normalized contributions (summing to 1.0).
     */
    fun getNormalizedContributions(): Map<ExplanationFactor, Float> {
        val total = totalWeightedScore
        if (total == 0f) return emptyMap()
        return _factors.associateWith { (it.weight * it.rawScore) / total }
    }

    /**
     * Get contribution percentages for display.
     */
    fun getContributionPercentages(): List<Pair<ExplanationFactor, Int>> {
        val contributions = getNormalizedContributions()
        return contributions.map { (factor, contribution) ->
            factor to (contribution * 100).toInt()
        }.sortedByDescending { it.second }
    }

    /**
     * Filter factors by reason type.
     */
    fun filterByReason(reason: ExplanationReason): List<ExplanationFactor> = _factors.filter { it.reason == reason }

    /**
     * Check if collection contains a specific reason type.
     */
    fun hasReason(reason: ExplanationReason): Boolean = _factors.any { it.reason == reason }

    companion object {
        fun of(vararg factors: ExplanationFactor) = FactorCollection(factors.toList())
        fun empty() = FactorCollection(emptyList())
    }
}
