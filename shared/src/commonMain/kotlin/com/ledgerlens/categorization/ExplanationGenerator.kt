package com.ledgerlens.categorization

/**
 * Generates human-readable explanations for category classifications.
 */
class ExplanationGenerator(
    private val stringProvider: LocalizedStringProvider = DefaultStringProvider()
) {
    /**
     * Generate a full explanation from classification result and factors.
     */
    fun generate(
        result: ClassificationResult,
        factors: FactorCollection,
        categoryName: String? = null
    ): CategoryExplanation {
        val confidence = ConfidenceLevel.fromScore(result.confidence)
        val primaryFactor = factors.primaryFactor

        val summary = buildSummary(result, factors, categoryName)
        val detailedFactors = buildDetailedFactors(factors)

        return CategoryExplanation(
            categoryId = result.categoryId,
            categoryName = categoryName,
            confidence = result.confidence,
            confidenceLevel = confidence,
            summary = summary,
            factors = factors.factors,
            primaryReason = primaryFactor?.reason ?: ExplanationReason.DEFAULT_FALLBACK,
            classifierUsed = result.explanation.classifierUsed
        )
    }

    /**
     * Generate explanation from a classification result only.
     */
    fun generateFromResult(
        result: ClassificationResult,
        categoryName: String? = null
    ): CategoryExplanation {
        val factors = extractFactorsFromResult(result)
        return generate(result, factors, categoryName)
    }

    private fun extractFactorsFromResult(result: ClassificationResult): FactorCollection {
        val factors = FactorCollection()
        val explanation = result.explanation

        // Add merchant prior if present
        explanation.merchantPrior?.let { prior ->
            factors.add(ExplanationFactor.merchantMatch(
                merchantName = "merchant",
                score = prior
            ))
        }

        // Add token matches as keyword factors
        for (token in explanation.tokenMatches) {
            factors.add(ExplanationFactor.keywordMatch(token, 0.5f))
        }

        // Add rule match if present
        explanation.ruleMatched?.let { rule ->
            factors.add(ExplanationFactor.ruleMatch(rule, 1.0f))
        }

        // Add amount pattern if present
        explanation.amountPattern?.let { pattern ->
            factors.add(ExplanationFactor.amountPattern(pattern, 0.3f))
        }

        // If no factors extracted, add a default
        if (factors.isEmpty) {
            factors.add(ExplanationFactor(
                reason = ExplanationReason.fromClassifier(explanation.classifierUsed),
                value = explanation.reason,
                weight = 1.0f,
                rawScore = result.confidence
            ))
        }

        return factors
    }

    private fun buildSummary(
        result: ClassificationResult,
        factors: FactorCollection,
        categoryName: String?
    ): String {
        val name = categoryName ?: result.categoryId
        val confidence = ConfidenceLevel.fromScore(result.confidence)
        val primary = factors.primaryFactor

        return when {
            primary == null -> 
                "Categorized as '$name' (${confidence.displayName})"
            
            primary.reason == ExplanationReason.RULE_MATCH ->
                "Matched rule '${primary.value}' → '$name'"
            
            primary.reason == ExplanationReason.MERCHANT_MATCH ->
                "Merchant '${primary.value}' is usually '$name'"
            
            primary.reason == ExplanationReason.KEYWORD_MATCH ->
                "Keyword '${primary.value}' suggests '$name'"
            
            primary.reason == ExplanationReason.USER_HISTORY ->
                "Similar transactions were '$name'"
            
            factors.size > 1 -> {
                val percentages = factors.getContributionPercentages().take(2)
                val parts = percentages.map { (f, pct) -> "${f.describe()} ($pct%)" }
                "${parts.joinToString(" + ")} → '$name'"
            }
            
            else -> "Categorized as '$name' based on ${primary.describe()}"
        }
    }

    private fun buildDetailedFactors(factors: FactorCollection): String {
        if (factors.isEmpty) return "No specific factors identified"

        val percentages = factors.getContributionPercentages()
        return percentages.joinToString("\n") { (factor, pct) ->
            "• ${factor.describe()}: $pct%"
        }
    }
}

/**
 * Confidence level for classifications.
 */
enum class ConfidenceLevel(val displayName: String, val minScore: Float) {
    VERY_HIGH("Very High", 0.9f),
    HIGH("High", 0.75f),
    MEDIUM("Medium", 0.5f),
    LOW("Low", 0.25f),
    VERY_LOW("Very Low", 0.0f);

    companion object {
        fun fromScore(score: Float): ConfidenceLevel {
            return entries.first { score >= it.minScore }
        }
    }
}

/**
 * Full explanation for a category assignment.
 */
data class CategoryExplanation(
    val categoryId: String,
    val categoryName: String?,
    val confidence: Float,
    val confidenceLevel: ConfidenceLevel,
    val summary: String,
    val factors: List<ExplanationFactor>,
    val primaryReason: ExplanationReason,
    val classifierUsed: String
) {
    /**
     * Get a short one-line explanation.
     */
    val shortExplanation: String
        get() = summary

    /**
     * Get accessible explanation for screen readers.
     */
    val accessibleExplanation: String
        get() = "Category: ${categoryName ?: categoryId}. " +
                "Confidence: ${confidenceLevel.displayName}. " +
                summary

    /**
     * Get compact explanation for lists.
     */
    val compactExplanation: String
        get() {
            val primary = factors.maxByOrNull { it.normalizedContribution }
            return primary?.describe() ?: "Auto-categorized"
        }
}

/**
 * Interface for providing localized strings.
 */
interface LocalizedStringProvider {
    fun getString(key: String, vararg args: Any): String
}

/**
 * Default string provider with English fallbacks.
 */
class DefaultStringProvider : LocalizedStringProvider {
    private val strings = mapOf(
        ExplanationLocalizationKeys.MERCHANT_MATCH_TEMPLATE to "Merchant '%s' is typically categorized as '%s'",
        ExplanationLocalizationKeys.KEYWORD_MATCH_TEMPLATE to "Contains keyword '%s'",
        ExplanationLocalizationKeys.RULE_MATCH_TEMPLATE to "Matched rule '%s'",
        ExplanationLocalizationKeys.USER_HISTORY_TEMPLATE to "Based on %d similar transactions",
        ExplanationLocalizationKeys.AMOUNT_PATTERN_TEMPLATE to "Amount matches '%s' pattern",
        ExplanationLocalizationKeys.COMBINED_FACTORS_TEMPLATE to "Multiple factors suggest '%s'",
        ExplanationLocalizationKeys.DEFAULT_TEMPLATE to "Auto-categorized as '%s'",
        ExplanationLocalizationKeys.CONFIDENCE_VERY_HIGH to "Very confident",
        ExplanationLocalizationKeys.CONFIDENCE_HIGH to "Confident",
        ExplanationLocalizationKeys.CONFIDENCE_MEDIUM to "Somewhat confident",
        ExplanationLocalizationKeys.CONFIDENCE_LOW to "Low confidence",
        ExplanationLocalizationKeys.CONFIDENCE_VERY_LOW to "Very low confidence"
    )

    override fun getString(key: String, vararg args: Any): String {
        val template = strings[key] ?: return key
        return if (args.isEmpty()) template else template.format(*args)
    }
}

/**
 * Formats explanations for different UI contexts.
 */
class ExplanationFormatter(
    private val stringProvider: LocalizedStringProvider = DefaultStringProvider()
) {
    /**
     * Format as a summary suitable for transaction lists.
     */
    fun formatSummary(explanation: CategoryExplanation): String =
        explanation.summary

    /**
     * Format with full details for category editing screens.
     */
    fun formatDetailed(explanation: CategoryExplanation): String {
        val lines = mutableListOf<String>()
        lines.add(explanation.summary)
        lines.add("")
        lines.add("Confidence: ${explanation.confidenceLevel.displayName} (${(explanation.confidence * 100).toInt()}%)")
        lines.add("")
        lines.add("Contributing factors:")
        
        val percentages = FactorCollection(explanation.factors).getContributionPercentages()
        for ((factor, pct) in percentages) {
            lines.add("  • ${factor.describe()}: $pct%")
        }
        
        return lines.joinToString("\n")
    }

    /**
     * Format for accessibility (screen readers).
     */
    fun formatAccessible(explanation: CategoryExplanation): String =
        explanation.accessibleExplanation

    /**
     * Format compact version for tight spaces.
     */
    fun formatCompact(explanation: CategoryExplanation): String =
        explanation.compactExplanation
}
