package com.ledgerlens.categorization

/**
 * Reasons why a category was assigned to a transaction.
 * Used for explainability and transparency in categorization.
 */
enum class ExplanationReason(
    val localizationKey: String,
    val displayOrder: Int
) {
    /**
     * Category matched based on merchant name lookup.
     */
    MERCHANT_MATCH("explanation.reason.merchant_match", 1),

    /**
     * Category matched based on keywords in description.
     */
    KEYWORD_MATCH("explanation.reason.keyword_match", 2),

    /**
     * Category inferred from transaction amount pattern.
     */
    AMOUNT_PATTERN("explanation.reason.amount_pattern", 3),

    /**
     * Category based on user's historical categorization patterns.
     */
    USER_HISTORY("explanation.reason.user_history", 4),

    /**
     * Category matched a user-defined rule.
     */
    RULE_MATCH("explanation.reason.rule_match", 5),

    /**
     * Category inferred from temporal patterns (day of week, time, etc.).
     */
    TEMPORAL_PATTERN("explanation.reason.temporal_pattern", 6),

    /**
     * Category inferred from account type or context.
     */
    ACCOUNT_CONTEXT("explanation.reason.account_context", 7),

    /**
     * Multiple factors combined to determine category.
     */
    COMBINED_FACTORS("explanation.reason.combined_factors", 8),

    /**
     * Default/fallback when no strong signal was found.
     */
    DEFAULT_FALLBACK("explanation.reason.default_fallback", 9);

    companion object {
        /**
         * Get default English description for a reason.
         */
        fun defaultDescription(reason: ExplanationReason): String = when (reason) {
            MERCHANT_MATCH -> "Merchant name matched"
            KEYWORD_MATCH -> "Keywords in description matched"
            AMOUNT_PATTERN -> "Amount pattern matched"
            USER_HISTORY -> "Based on your past categorizations"
            RULE_MATCH -> "Matched a custom rule"
            TEMPORAL_PATTERN -> "Based on timing pattern"
            ACCOUNT_CONTEXT -> "Based on account type"
            COMBINED_FACTORS -> "Multiple factors combined"
            DEFAULT_FALLBACK -> "Default category assigned"
        }
    }
}
