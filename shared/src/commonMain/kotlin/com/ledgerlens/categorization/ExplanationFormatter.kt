package com.ledgerlens.categorization

/**
 * Formats CategoryExplanation for UI display.
 * Supports localization through string keys and provides multiple output formats.
 */
class ExplanationFormatter(
    private val stringProvider: LocalizedStringProvider = DefaultStringProvider
) {

    /**
     * Format explanation as a single-line summary.
     */
    fun formatSummary(explanation: CategoryExplanation): String {
        return explanation.humanReadableSummary
            ?: generateDefaultSummary(explanation)
    }

    /**
     * Format explanation with full factor breakdown.
     */
    fun formatDetailed(explanation: CategoryExplanation): FormattedExplanation {
        val summaryLine = formatSummary(explanation)
        val confidenceLine = formatConfidence(explanation)
        val factorLines = formatFactors(explanation.factors)

        return FormattedExplanation(
            summary = summaryLine,
            confidence = confidenceLine,
            factors = factorLines,
            classifier = explanation.classifierSource
        )
    }

    /**
     * Format just the confidence information.
     */
    fun formatConfidence(explanation: CategoryExplanation): String {
        val level = explanation.confidenceLevel
        val levelText = stringProvider.getString(level.localizationKey)
            ?: ConfidenceLevel.defaultDescription(level)
        val percentage = (explanation.overallConfidence * 100).toInt()
        return "$levelText ($percentage%)"
    }

    /**
     * Format factors as a list of strings.
     */
    fun formatFactors(factors: FactorCollection): List<FormattedFactor> {
        return factors.sortedByWeight.map { factor ->
            FormattedFactor(
                description = formatFactor(factor),
                percentage = factor.weightPercent,
                reason = factor.reason,
                isSignificant = factor.isSignificant
            )
        }
    }

    /**
     * Format a single factor.
     */
    fun formatFactor(factor: ExplanationFactor): String {
        val reasonText = stringProvider.getString(factor.reason.localizationKey)
            ?: ExplanationReason.defaultDescription(factor.reason)

        return when (factor.reason) {
            ExplanationReason.MERCHANT_MATCH -> {
                val matchType = factor.metadata["matchType"] ?: "exact"
                formatMerchantFactor(factor.value, matchType)
            }
            ExplanationReason.KEYWORD_MATCH -> {
                "Keyword: '${factor.value}'"
            }
            ExplanationReason.AMOUNT_PATTERN -> {
                val range = factor.metadata["range"]
                if (range != null) {
                    "Amount pattern: ${factor.value} ($range)"
                } else {
                    "Amount pattern: ${factor.value}"
                }
            }
            ExplanationReason.USER_HISTORY -> {
                val occurrences = factor.metadata["occurrences"]
                if (occurrences != null) {
                    "Your history: ${factor.value} ($occurrences times)"
                } else {
                    "Your history: ${factor.value}"
                }
            }
            ExplanationReason.RULE_MATCH -> {
                "Rule: '${factor.value}'"
            }
            ExplanationReason.TEMPORAL_PATTERN -> {
                "Timing: ${factor.value}"
            }
            ExplanationReason.ACCOUNT_CONTEXT -> {
                "Account: ${factor.value}"
            }
            else -> {
                "$reasonText: ${factor.value}"
            }
        }
    }

    /**
     * Format explanation for accessibility (screen readers).
     */
    fun formatAccessible(explanation: CategoryExplanation): String = buildString {
        append("Category explanation. ")
        append(formatSummary(explanation))
        append(". ")
        append("Confidence: ${formatConfidence(explanation)}. ")

        val significantFactors = explanation.factors.significantFactors
        if (significantFactors.isNotEmpty()) {
            append("Based on ${significantFactors.size} factors: ")
            significantFactors.forEachIndexed { index, factor ->
                if (index > 0) append(", ")
                append("${formatFactor(factor)} at ${factor.weightPercent} percent")
            }
            append(".")
        }
    }

    /**
     * Format as a compact inline string (for tooltips, etc.).
     */
    fun formatCompact(explanation: CategoryExplanation): String {
        val primary = explanation.factors.primaryFactor
            ?: return "Unknown"

        val confidence = (explanation.overallConfidence * 100).toInt()
        return "${primary.value} ($confidence%)"
    }

    private fun generateDefaultSummary(explanation: CategoryExplanation): String {
        val primary = explanation.factors.primaryFactor
        return if (primary != null) {
            "${ExplanationReason.defaultDescription(primary.reason)}: '${primary.value}'"
        } else {
            ExplanationReason.defaultDescription(explanation.primaryReason)
        }
    }

    private fun formatMerchantFactor(merchant: String, matchType: String): String {
        return when (matchType.lowercase()) {
            "exact" -> "Merchant: '$merchant'"
            "normalized" -> "Merchant (normalized): '$merchant'"
            "alias" -> "Merchant (alias): '$merchant'"
            "fuzzy" -> "Merchant (similar): '$merchant'"
            else -> "Merchant: '$merchant'"
        }
    }
}

/**
 * Structured formatted explanation for UI display.
 */
data class FormattedExplanation(
    val summary: String,
    val confidence: String,
    val factors: List<FormattedFactor>,
    val classifier: String
) {
    /**
     * Convert to multi-line string.
     */
    fun toMultiLine(): String = buildString {
        appendLine(summary)
        appendLine("Confidence: $confidence")
        if (factors.isNotEmpty()) {
            appendLine("Contributing factors:")
            factors.forEach { factor ->
                appendLine("  • ${factor.description} (${factor.percentage}%)")
            }
        }
    }
}

/**
 * A single formatted factor for display.
 */
data class FormattedFactor(
    val description: String,
    val percentage: Int,
    val reason: ExplanationReason,
    val isSignificant: Boolean
)

/**
 * Interface for providing localized strings.
 */
interface LocalizedStringProvider {
    fun getString(key: String): String?
    fun getString(key: String, vararg args: Any): String?
}

/**
 * Default string provider using built-in English strings.
 */
object DefaultStringProvider : LocalizedStringProvider {
    private val strings = mapOf(
        // Explanation reasons
        "explanation.reason.merchant_match" to "Merchant name matched",
        "explanation.reason.keyword_match" to "Keywords in description matched",
        "explanation.reason.amount_pattern" to "Amount pattern matched",
        "explanation.reason.user_history" to "Based on your past categorizations",
        "explanation.reason.rule_match" to "Matched a custom rule",
        "explanation.reason.temporal_pattern" to "Based on timing pattern",
        "explanation.reason.account_context" to "Based on account type",
        "explanation.reason.combined_factors" to "Multiple factors combined",
        "explanation.reason.default_fallback" to "Default category assigned",

        // Confidence levels
        "confidence.very_high" to "Very confident",
        "confidence.high" to "Confident",
        "confidence.medium" to "Moderately confident",
        "confidence.low" to "Low confidence",
        "confidence.very_low" to "Very low confidence",

        // UI strings
        "explanation.no_category" to "No category could be determined",
        "explanation.based_on" to "Based on",
        "explanation.contributing_factors" to "Contributing factors"
    )

    override fun getString(key: String): String? = strings[key]

    override fun getString(key: String, vararg args: Any): String? {
        val template = strings[key] ?: return null
        return try {
            args.foldIndexed(template) { index, acc, arg ->
                acc.replace("{$index}", arg.toString())
            }
        } catch (e: Exception) {
            template
        }
    }
}

/**
 * Localization keys used by the explanation system.
 * Use these keys to provide translations in your app.
 */
object ExplanationLocalizationKeys {
    // Reason keys
    const val REASON_MERCHANT_MATCH = "explanation.reason.merchant_match"
    const val REASON_KEYWORD_MATCH = "explanation.reason.keyword_match"
    const val REASON_AMOUNT_PATTERN = "explanation.reason.amount_pattern"
    const val REASON_USER_HISTORY = "explanation.reason.user_history"
    const val REASON_RULE_MATCH = "explanation.reason.rule_match"
    const val REASON_TEMPORAL_PATTERN = "explanation.reason.temporal_pattern"
    const val REASON_ACCOUNT_CONTEXT = "explanation.reason.account_context"
    const val REASON_COMBINED_FACTORS = "explanation.reason.combined_factors"
    const val REASON_DEFAULT_FALLBACK = "explanation.reason.default_fallback"

    // Confidence keys
    const val CONFIDENCE_VERY_HIGH = "confidence.very_high"
    const val CONFIDENCE_HIGH = "confidence.high"
    const val CONFIDENCE_MEDIUM = "confidence.medium"
    const val CONFIDENCE_LOW = "confidence.low"
    const val CONFIDENCE_VERY_LOW = "confidence.very_low"

    // UI keys
    const val NO_CATEGORY = "explanation.no_category"
    const val BASED_ON = "explanation.based_on"
    const val CONTRIBUTING_FACTORS = "explanation.contributing_factors"

    /**
     * All localization keys that need translations.
     */
    val allKeys: List<String> = listOf(
        REASON_MERCHANT_MATCH,
        REASON_KEYWORD_MATCH,
        REASON_AMOUNT_PATTERN,
        REASON_USER_HISTORY,
        REASON_RULE_MATCH,
        REASON_TEMPORAL_PATTERN,
        REASON_ACCOUNT_CONTEXT,
        REASON_COMBINED_FACTORS,
        REASON_DEFAULT_FALLBACK,
        CONFIDENCE_VERY_HIGH,
        CONFIDENCE_HIGH,
        CONFIDENCE_MEDIUM,
        CONFIDENCE_LOW,
        CONFIDENCE_VERY_LOW,
        NO_CATEGORY,
        BASED_ON,
        CONTRIBUTING_FACTORS
    )
}
