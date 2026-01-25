package com.ledgerlens.categorization

import kotlinx.datetime.Instant

/**
 * Represents a single category correction made by the user.
 */
data class CategoryCorrection(
    val id: String,
    val transactionId: String,
    val oldCategoryId: String,
    val newCategoryId: String,
    val timestamp: Instant,
    val features: TransactionFeatures,
    val confidence: Float,
    val classifierUsed: String
) {
    /**
     * Returns true if this is an actual correction (categories differ).
     * False if the user confirmed the predicted category.
     */
    val isActualCorrection: Boolean
        get() = oldCategoryId != newCategoryId

    /**
     * Returns true if the classifier was highly confident but still wrong.
     * This indicates a potential systematic issue that may need rule adjustment.
     */
    val wasHighConfidenceMiss: Boolean
        get() = isActualCorrection && confidence >= HIGH_CONFIDENCE_THRESHOLD

    companion object {
        /** Threshold above which a prediction is considered "high confidence" */
        const val HIGH_CONFIDENCE_THRESHOLD = 0.85f
    }
}

/**
 * A rule suggestion generated from analyzing user corrections.
 */
data class SuggestedRule(
    val merchantPattern: String,
    val categoryId: String,
    val supportingCorrections: Int,
    val confidence: Float,
    val reason: String
)

/**
 * Statistics about corrections for a merchant or category.
 */
data class CorrectionStats(
    val totalPredictions: Int,
    val totalCorrections: Int,
    val correctionsByCategory: Map<String, Int>,
    val mostCommonCorrection: Pair<String, String>?
) {
    /**
     * Rate of corrections (0.0 to 1.0).
     */
    val correctionRate: Float
        get() = if (totalPredictions > 0) totalCorrections.toFloat() / totalPredictions else 0f
}

/**
 * Analysis results from examining all stored corrections.
 */
data class CorrectionAnalysis(
    val merchantStats: Map<String, CorrectionStats>,
    val categoryStats: Map<String, CorrectionStats>,
    val suggestedRules: List<SuggestedRule>,
    val totalCorrections: Int,
    val overallCorrectionRate: Float
)
