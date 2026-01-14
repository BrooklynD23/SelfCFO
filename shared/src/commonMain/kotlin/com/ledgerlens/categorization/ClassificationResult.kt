package com.ledgerlens.categorization

/**
 * Result of classifying a transaction into a category.
 *
 * @property categoryId The predicted category ID
 * @property confidence Confidence score from 0.0 to 1.0
 * @property alternatives Other possible categories with their scores
 * @property explanation Structured explanation of why this category was chosen
 */
data class ClassificationResult(
    val categoryId: String,
    val confidence: Float,
    val alternatives: List<CategoryScore> = emptyList(),
    val explanation: ClassificationExplanation
) {
    /**
     * Whether the classification has high enough confidence to auto-apply.
     */
    val isHighConfidence: Boolean get() = confidence >= HIGH_CONFIDENCE_THRESHOLD

    /**
     * Whether the classification needs user review.
     */
    val needsReview: Boolean get() = confidence < REVIEW_THRESHOLD

    companion object {
        const val HIGH_CONFIDENCE_THRESHOLD = 0.85f
        const val REVIEW_THRESHOLD = 0.5f

        /**
         * Create an unknown/unclassified result.
         */
        fun unknown(): ClassificationResult = ClassificationResult(
            categoryId = UNKNOWN_CATEGORY_ID,
            confidence = 0.0f,
            alternatives = emptyList(),
            explanation = ClassificationExplanation(
                classifierUsed = "none",
                reason = "No classifier could determine a category"
            )
        )

        const val UNKNOWN_CATEGORY_ID = "unknown"
    }
}

/**
 * A category with its confidence score.
 */
data class CategoryScore(
    val categoryId: String,
    val score: Float
)

/**
 * Structured explanation of a classification decision.
 *
 * @property classifierUsed Which classifier produced this result
 * @property reason Human-readable explanation
 * @property ruleMatched The rule ID if a rule was matched
 * @property merchantPrior Merchant-based prior probability if used
 * @property tokenMatches Description tokens that influenced the decision
 * @property amountPattern Amount range that influenced the decision
 */
data class ClassificationExplanation(
    val classifierUsed: String,
    val reason: String,
    val ruleMatched: String? = null,
    val merchantPrior: Float? = null,
    val tokenMatches: List<String> = emptyList(),
    val amountPattern: String? = null
) {
    /**
     * Generate a user-friendly summary of the explanation.
     */
    fun toUserSummary(): String = buildString {
        append(reason)
        if (ruleMatched != null) {
            append(" (Rule: $ruleMatched)")
        }
        if (tokenMatches.isNotEmpty()) {
            append(" [Keywords: ${tokenMatches.take(3).joinToString(", ")}]")
        }
    }
}

/**
 * Input data for classification - extracted features from a transaction.
 */
data class TransactionFeatures(
    val merchantNormalized: String,
    val descriptionRaw: String,
    val descriptionTokens: List<String>,
    val amountCents: Long,
    val amountBucket: AmountBucket,
    val isDebit: Boolean,
    val dayOfWeek: Int,
    val dayOfMonth: Int,
    val accountId: String? = null
)

/**
 * Predefined amount buckets for feature extraction.
 */
enum class AmountBucket {
    MICRO,      // < $5
    SMALL,      // $5 - $25
    MEDIUM,     // $25 - $100
    LARGE,      // $100 - $500
    VERY_LARGE, // $500 - $2000
    HUGE;       // > $2000

    companion object {
        fun fromCents(amountCents: Long): AmountBucket {
            val absAmount = kotlin.math.abs(amountCents)
            return when {
                absAmount < 500 -> MICRO
                absAmount < 2500 -> SMALL
                absAmount < 10000 -> MEDIUM
                absAmount < 50000 -> LARGE
                absAmount < 200000 -> VERY_LARGE
                else -> HUGE
            }
        }
    }
}
