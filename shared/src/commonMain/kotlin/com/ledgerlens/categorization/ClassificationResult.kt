package com.ledgerlens.categorization

data class ClassificationResult(
    val categoryId: String, val confidence: Float,
    val alternatives: List<CategoryScore> = emptyList(),
    val explanation: ClassificationExplanation
) {
    val isHighConfidence: Boolean get() = confidence >= HIGH_CONFIDENCE_THRESHOLD
    val needsReview: Boolean get() = confidence < REVIEW_THRESHOLD
    companion object {
        const val HIGH_CONFIDENCE_THRESHOLD = 0.85f
        const val REVIEW_THRESHOLD = 0.5f
        const val UNKNOWN_CATEGORY_ID = "unknown"
        fun unknown() = ClassificationResult(UNKNOWN_CATEGORY_ID, 0.0f, emptyList(),
            ClassificationExplanation("none", "No classifier could determine a category"))
    }
}
data class CategoryScore(val categoryId: String, val score: Float)
data class ClassificationExplanation(val classifierUsed: String, val reason: String, val ruleMatched: String? = null,
    val merchantPrior: Float? = null, val tokenMatches: List<String> = emptyList(), val amountPattern: String? = null)
data class TransactionFeatures(val merchantNormalized: String, val descriptionRaw: String, val descriptionTokens: List<String>,
    val amountCents: Long, val amountBucket: AmountBucket, val isDebit: Boolean, val dayOfWeek: Int, val dayOfMonth: Int, val accountId: String? = null)
enum class AmountBucket {
    MICRO, SMALL, MEDIUM, LARGE, VERY_LARGE, HUGE;
    companion object {
        fun fromCents(amountCents: Long): AmountBucket {
            val abs = kotlin.math.abs(amountCents)
            return when { abs < 500 -> MICRO; abs < 2500 -> SMALL; abs < 10000 -> MEDIUM; abs < 50000 -> LARGE; abs < 200000 -> VERY_LARGE; else -> HUGE }
        }
    }
}
