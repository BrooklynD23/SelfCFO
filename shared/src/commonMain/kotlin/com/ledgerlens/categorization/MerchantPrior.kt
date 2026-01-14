package com.ledgerlens.categorization

import kotlin.math.exp

/**
 * Represents the category assignment history for a specific merchant.
 * Used for Bayesian inference of category probabilities based on past assignments.
 *
 * @property merchantId Normalized merchant identifier (from MerchantNormalizer)
 * @property categoryCounts Map of categoryId to number of times assigned
 * @property lastCategoryId Most recently assigned category
 * @property totalTransactions Total number of categorized transactions for this merchant
 * @property lastUpdatedEpochMs Timestamp of last update in milliseconds since epoch
 */
data class MerchantPrior(
    val merchantId: String,
    val categoryCounts: Map<String, Int>,
    val lastCategoryId: String?,
    val totalTransactions: Int,
    val lastUpdatedEpochMs: Long
) {
    init {
        require(merchantId.isNotBlank()) { "Merchant ID cannot be blank" }
        require(totalTransactions >= 0) { "Total transactions cannot be negative" }
        require(categoryCounts.values.all { it >= 0 }) { "Category counts cannot be negative" }
    }

    /**
     * Returns true if this merchant has any category history.
     */
    val hasHistory: Boolean
        get() = totalTransactions > 0

    /**
     * Returns the most frequently assigned category, or null if no history.
     */
    val dominantCategoryId: String?
        get() = categoryCounts.maxByOrNull { it.value }?.key

    /**
     * Returns the count for a specific category.
     */
    fun countForCategory(categoryId: String): Int = categoryCounts[categoryId] ?: 0

    /**
     * Returns the raw frequency (proportion) for a specific category.
     */
    fun frequencyForCategory(categoryId: String): Float {
        if (totalTransactions == 0) return 0f
        return countForCategory(categoryId).toFloat() / totalTransactions
    }

    companion object {
        /**
         * Create an empty prior for a new merchant.
         */
        fun empty(merchantId: String, currentTimeMs: Long = System.currentTimeMillis()): MerchantPrior {
            return MerchantPrior(
                merchantId = merchantId,
                categoryCounts = emptyMap(),
                lastCategoryId = null,
                totalTransactions = 0,
                lastUpdatedEpochMs = currentTimeMs
            )
        }
    }
}

/**
 * Category probability with supporting information.
 */
data class CategoryProbability(
    val categoryId: String,
    val probability: Float,
    val observedCount: Int,
    val decayedWeight: Float
) {
    init {
        require(probability in 0f..1f) { "Probability must be between 0 and 1" }
    }
}

/**
 * Result of computing category probabilities for a merchant.
 */
data class MerchantCategoryDistribution(
    val merchantId: String,
    val probabilities: List<CategoryProbability>,
    val totalObservations: Int,
    val effectiveObservations: Float
) {
    /**
     * Get probability for a specific category, or 0 if not present.
     */
    fun probabilityFor(categoryId: String): Float {
        return probabilities.find { it.categoryId == categoryId }?.probability ?: 0f
    }

    /**
     * Get the most likely category, or null if no data.
     */
    val mostLikelyCategory: CategoryProbability?
        get() = probabilities.maxByOrNull { it.probability }

    /**
     * Returns true if there's sufficient data for reliable inference.
     */
    val isReliable: Boolean
        get() = effectiveObservations >= MIN_RELIABLE_OBSERVATIONS

    companion object {
        const val MIN_RELIABLE_OBSERVATIONS = 3f

        /**
         * Create an empty distribution (no history).
         */
        fun empty(merchantId: String): MerchantCategoryDistribution {
            return MerchantCategoryDistribution(
                merchantId = merchantId,
                probabilities = emptyList(),
                totalObservations = 0,
                effectiveObservations = 0f
            )
        }
    }
}

/**
 * Parameters for Bayesian prior calculation.
 */
data class PriorCalculationParams(
    val decayHalfLifeDays: Float = DEFAULT_DECAY_HALF_LIFE_DAYS,
    val pseudoCount: Float = DEFAULT_PSEUDO_COUNT,
    val minObservationsForPrior: Int = DEFAULT_MIN_OBSERVATIONS
) {
    init {
        require(decayHalfLifeDays > 0) { "Decay half-life must be positive" }
        require(pseudoCount >= 0) { "Pseudo count cannot be negative" }
        require(minObservationsForPrior >= 0) { "Min observations cannot be negative" }
    }

    companion object {
        const val DEFAULT_DECAY_HALF_LIFE_DAYS = 90f
        const val DEFAULT_PSEUDO_COUNT = 1f
        const val DEFAULT_MIN_OBSERVATIONS = 1
    }
}
