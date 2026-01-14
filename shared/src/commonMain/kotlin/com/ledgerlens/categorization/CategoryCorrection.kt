package com.ledgerlens.categorization

import kotlinx.datetime.Instant

/**
 * Represents a user correction to a category prediction.
 *
 * @property id Unique identifier for this correction
 * @property transactionId The transaction that was corrected
 * @property oldCategoryId The category that was predicted (and rejected)
 * @property newCategoryId The category the user selected
 * @property timestamp When the correction was made
 * @property features The transaction features at time of correction
 * @property confidence The original prediction confidence
 * @property classifierUsed Which classifier made the original prediction
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
     * Whether this correction changes the category (not just a confirmation).
     */
    val isActualCorrection: Boolean get() = oldCategoryId != newCategoryId

    /**
     * Whether the original prediction was high confidence but still wrong.
     */
    val wasHighConfidenceMiss: Boolean get() =
        isActualCorrection && confidence >= ClassificationResult.HIGH_CONFIDENCE_THRESHOLD
}

/**
 * Summary statistics for corrections on a specific merchant or category.
 */
data class CorrectionStats(
    val totalPredictions: Int,
    val totalCorrections: Int,
    val correctionsByCategory: Map<String, Int>,
    val mostCommonCorrection: Pair<String, String>?
) {
    /**
     * Correction rate as a percentage.
     */
    val correctionRate: Float get() =
        if (totalPredictions > 0) totalCorrections.toFloat() / totalPredictions else 0f

    /**
     * Whether this entity is frequently corrected.
     */
    val isFrequentlyCorrected: Boolean get() =
        correctionRate >= FREQUENT_CORRECTION_THRESHOLD && totalCorrections >= MIN_CORRECTIONS_FOR_FREQUENT

    companion object {
        const val FREQUENT_CORRECTION_THRESHOLD = 0.3f
        const val MIN_CORRECTIONS_FOR_FREQUENT = 3
    }
}

/**
 * Suggested rule based on repeated corrections.
 */
data class SuggestedRule(
    val merchantPattern: String,
    val suggestedCategoryId: String,
    val supportingCorrections: Int,
    val confidence: Float,
    val reason: String
)

/**
 * Result of analyzing corrections for patterns.
 */
data class CorrectionAnalysis(
    val merchantStats: Map<String, CorrectionStats>,
    val categoryStats: Map<String, CorrectionStats>,
    val suggestedRules: List<SuggestedRule>,
    val totalCorrections: Int,
    val averageCorrectionRate: Float
)
