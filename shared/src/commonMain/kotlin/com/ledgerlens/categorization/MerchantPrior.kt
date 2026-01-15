package com.ledgerlens.categorization

/**
 * Represents the historical category assignment data for a merchant.
 * Used to calculate Bayesian priors for category prediction.
 */
data class MerchantPrior(
    val merchantId: String,
    val categoryCounts: Map<String, Int>,
    val totalTransactions: Int,
    val lastUpdatedMs: Long = System.currentTimeMillis(),
    val firstSeenMs: Long = System.currentTimeMillis()
) {
    val dominantCategory: String?
        get() = categoryCounts.maxByOrNull { it.value }?.key

    val dominantCategoryCount: Int
        get() = categoryCounts.maxOfOrNull { it.value } ?: 0

    val dominantCategoryRatio: Float
        get() = if (totalTransactions > 0) {
            dominantCategoryCount.toFloat() / totalTransactions
        } else 0f

    val isHighConfidence: Boolean
        get() = totalTransactions >= MIN_RELIABLE_OBSERVATIONS &&
                dominantCategoryRatio >= HIGH_CONFIDENCE_RATIO

    val categoryProbabilities: Map<String, Float>
        get() = if (totalTransactions > 0) {
            categoryCounts.mapValues { it.value.toFloat() / totalTransactions }
        } else emptyMap()

    fun withAssignment(categoryId: String, timestampMs: Long = System.currentTimeMillis()): MerchantPrior {
        val newCounts = categoryCounts.toMutableMap()
        newCounts[categoryId] = (newCounts[categoryId] ?: 0) + 1
        return copy(
            categoryCounts = newCounts,
            totalTransactions = totalTransactions + 1,
            lastUpdatedMs = timestampMs
        )
    }

    fun mergeWith(other: MerchantPrior): MerchantPrior {
        require(merchantId == other.merchantId) { "Cannot merge priors for different merchants" }
        val mergedCounts = categoryCounts.toMutableMap()
        for ((cat, count) in other.categoryCounts) {
            mergedCounts[cat] = (mergedCounts[cat] ?: 0) + count
        }
        return MerchantPrior(
            merchantId = merchantId,
            categoryCounts = mergedCounts,
            totalTransactions = totalTransactions + other.totalTransactions,
            lastUpdatedMs = maxOf(lastUpdatedMs, other.lastUpdatedMs),
            firstSeenMs = minOf(firstSeenMs, other.firstSeenMs)
        )
    }

    companion object {
        const val MIN_RELIABLE_OBSERVATIONS = 3
        const val HIGH_CONFIDENCE_RATIO = 0.8f

        fun create(merchantId: String, categoryId: String, timestampMs: Long = System.currentTimeMillis()): MerchantPrior {
            return MerchantPrior(
                merchantId = merchantId,
                categoryCounts = mapOf(categoryId to 1),
                totalTransactions = 1,
                lastUpdatedMs = timestampMs,
                firstSeenMs = timestampMs
            )
        }
    }
}

/**
 * Category probability within a distribution.
 */
data class CategoryProbability(
    val categoryId: String,
    val probability: Float,
    val observationCount: Int
)

/**
 * Distribution of category probabilities for a merchant.
 */
data class MerchantCategoryDistribution(
    val merchantId: String,
    val probabilities: List<CategoryProbability>,
    val totalObservations: Int,
    val hasReliableHistory: Boolean
) {
    val mostLikelyCategory: CategoryProbability?
        get() = probabilities.maxByOrNull { it.probability }

    val entropy: Float
        get() {
            if (probabilities.isEmpty()) return 0f
            var e = 0f
            for (p in probabilities) {
                if (p.probability > 0) {
                    e -= p.probability * kotlin.math.ln(p.probability)
                }
            }
            return e
        }

    val isUniform: Boolean
        get() = probabilities.isEmpty() || 
                (probabilities.size > 1 && entropy > kotlin.math.ln(probabilities.size.toFloat()) * 0.9f)

    fun getProbability(categoryId: String): Float =
        probabilities.find { it.categoryId == categoryId }?.probability ?: 0f

    companion object {
        const val MIN_RELIABLE_OBSERVATIONS = 3

        fun empty(merchantId: String) = MerchantCategoryDistribution(
            merchantId = merchantId,
            probabilities = emptyList(),
            totalObservations = 0,
            hasReliableHistory = false
        )
    }
}
