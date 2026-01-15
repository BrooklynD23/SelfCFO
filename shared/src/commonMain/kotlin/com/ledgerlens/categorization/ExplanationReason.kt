package com.ledgerlens.categorization

/**
 * Enum representing the different reasons why a category was selected.
 * Used for generating human-readable explanations of classification decisions.
 */
enum class ExplanationReason(
    val displayNameKey: String,
    val descriptionKey: String,
    val weight: Float = 1.0f
) {
    /**
     * Category assigned based on merchant name matching.
     */
    MERCHANT_MATCH(
        displayNameKey = "explanation.reason.merchant_match",
        descriptionKey = "explanation.reason.merchant_match.description",
        weight = 1.5f
    ),

    /**
     * Category assigned based on keyword/token matching in description.
     */
    KEYWORD_MATCH(
        displayNameKey = "explanation.reason.keyword_match",
        descriptionKey = "explanation.reason.keyword_match.description",
        weight = 1.0f
    ),

    /**
     * Category assigned based on transaction amount pattern.
     */
    AMOUNT_PATTERN(
        displayNameKey = "explanation.reason.amount_pattern",
        descriptionKey = "explanation.reason.amount_pattern.description",
        weight = 0.5f
    ),

    /**
     * Category assigned based on user's historical categorization.
     */
    USER_HISTORY(
        displayNameKey = "explanation.reason.user_history",
        descriptionKey = "explanation.reason.user_history.description",
        weight = 1.2f
    ),

    /**
     * Category assigned based on a user-defined rule match.
     */
    RULE_MATCH(
        displayNameKey = "explanation.reason.rule_match",
        descriptionKey = "explanation.reason.rule_match.description",
        weight = 2.0f
    ),

    /**
     * Category assigned based on temporal patterns (day of week, time of day).
     */
    TEMPORAL_PATTERN(
        displayNameKey = "explanation.reason.temporal_pattern",
        descriptionKey = "explanation.reason.temporal_pattern.description",
        weight = 0.3f
    ),

    /**
     * Category assigned based on the account type or context.
     */
    ACCOUNT_CONTEXT(
        displayNameKey = "explanation.reason.account_context",
        descriptionKey = "explanation.reason.account_context.description",
        weight = 0.4f
    ),

    /**
     * Category assigned based on multiple combined factors.
     */
    COMBINED_FACTORS(
        displayNameKey = "explanation.reason.combined_factors",
        descriptionKey = "explanation.reason.combined_factors.description",
        weight = 1.0f
    ),

    /**
     * Default/fallback when no specific reason can be determined.
     */
    DEFAULT_FALLBACK(
        displayNameKey = "explanation.reason.default_fallback",
        descriptionKey = "explanation.reason.default_fallback.description",
        weight = 0.1f
    );

    companion object {
        /**
         * Get explanation reason from classifier name.
         */
        fun fromClassifier(classifierName: String): ExplanationReason {
            return when {
                classifierName.contains("merchant", ignoreCase = true) -> MERCHANT_MATCH
                classifierName.contains("rule", ignoreCase = true) -> RULE_MATCH
                classifierName.contains("naive-bayes", ignoreCase = true) -> KEYWORD_MATCH
                classifierName.contains("history", ignoreCase = true) -> USER_HISTORY
                classifierName.contains("ensemble", ignoreCase = true) -> COMBINED_FACTORS
                else -> DEFAULT_FALLBACK
            }
        }
    }
}

/**
 * Localization keys for explanation messages.
 */
object ExplanationLocalizationKeys {
    const val MERCHANT_MATCH_TEMPLATE = "explanation.template.merchant_match"
    const val KEYWORD_MATCH_TEMPLATE = "explanation.template.keyword_match"
    const val RULE_MATCH_TEMPLATE = "explanation.template.rule_match"
    const val USER_HISTORY_TEMPLATE = "explanation.template.user_history"
    const val AMOUNT_PATTERN_TEMPLATE = "explanation.template.amount_pattern"
    const val COMBINED_FACTORS_TEMPLATE = "explanation.template.combined_factors"
    const val DEFAULT_TEMPLATE = "explanation.template.default"

    const val CONFIDENCE_VERY_HIGH = "explanation.confidence.very_high"
    const val CONFIDENCE_HIGH = "explanation.confidence.high"
    const val CONFIDENCE_MEDIUM = "explanation.confidence.medium"
    const val CONFIDENCE_LOW = "explanation.confidence.low"
    const val CONFIDENCE_VERY_LOW = "explanation.confidence.very_low"
}
