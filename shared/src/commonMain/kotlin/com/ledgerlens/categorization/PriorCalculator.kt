package com.ledgerlens.categorization

import kotlin.math.ln
import kotlin.math.exp

/**
 * Calculates Bayesian priors and probability distributions for categories.
 * Uses Laplace (add-one) smoothing for unseen categories.
 */
class PriorCalculator(
    private val smoothingFactor: Float = DEFAULT_SMOOTHING,
    private val decayFactor: Float = DEFAULT_DECAY,
    private val minObservationsForReliable: Int = DEFAULT_MIN_OBSERVATIONS
) {
    /**
     * Calculate probability distribution from a merchant prior.
     * Applies Laplace smoothing to handle unseen categories.
     *
     * @param prior The merchant's historical category data
     * @param allCategoryIds Optional set of all possible categories for smoothing
     * @return A normalized probability distribution
     */
    fun calculateProbabilities(
        prior: MerchantPrior,
        allCategoryIds: Set<String>? = null
    ): MerchantCategoryDistribution {
        if (prior.totalTransactions == 0) {
            return MerchantCategoryDistribution.empty(prior.merchantId)
        }

        val categories = allCategoryIds ?: prior.categoryCounts.keys
        val numCategories = categories.size.coerceAtLeast(1)
        val denominator = prior.totalTransactions + smoothingFactor * numCategories

        val probabilities = categories.map { categoryId ->
            val count = prior.categoryCounts[categoryId] ?: 0
            val smoothedProb = (count + smoothingFactor) / denominator
            CategoryProbability(
                categoryId = categoryId,
                probability = smoothedProb,
                observationCount = count
            )
        }.sortedByDescending { it.probability }

        return MerchantCategoryDistribution(
            merchantId = prior.merchantId,
            probabilities = probabilities,
            totalObservations = prior.totalTransactions,
            hasReliableHistory = prior.totalTransactions >= minObservationsForReliable
        )
    }

    /**
     * Calculate log-probabilities for numerical stability in classification.
     */
    fun calculateLogProbabilities(
        prior: MerchantPrior,
        allCategoryIds: Set<String>? = null
    ): Map<String, Double> {
        val distribution = calculateProbabilities(prior, allCategoryIds)
        return distribution.probabilities.associate { 
            it.categoryId to ln(it.probability.toDouble().coerceAtLeast(1e-10))
        }
    }

    /**
     * Combine multiple priors with optional time decay.
     * More recent observations can be weighted more heavily.
     *
     * @param priors List of priors to combine
     * @param currentTimeMs Current time for decay calculation
     * @return Combined prior
     */
    fun combinePriors(
        priors: List<MerchantPrior>,
        currentTimeMs: Long = System.currentTimeMillis()
    ): MerchantPrior? {
        if (priors.isEmpty()) return null
        if (priors.size == 1) return priors.first()

        val merchantId = priors.first().merchantId
        require(priors.all { it.merchantId == merchantId }) { 
            "All priors must be for the same merchant" 
        }

        val combinedCounts = mutableMapOf<String, Int>()
        var totalCount = 0
        var earliestSeen = Long.MAX_VALUE
        var latestUpdate = 0L

        for (prior in priors) {
            val weight = if (decayFactor < 1.0f) {
                calculateDecayWeight(prior.lastUpdatedMs, currentTimeMs)
            } else 1.0f

            for ((categoryId, count) in prior.categoryCounts) {
                val weightedCount = (count * weight).toInt().coerceAtLeast(if (count > 0) 1 else 0)
                combinedCounts[categoryId] = (combinedCounts[categoryId] ?: 0) + weightedCount
                totalCount += weightedCount
            }

            earliestSeen = minOf(earliestSeen, prior.firstSeenMs)
            latestUpdate = maxOf(latestUpdate, prior.lastUpdatedMs)
        }

        return MerchantPrior(
            merchantId = merchantId,
            categoryCounts = combinedCounts,
            totalTransactions = totalCount,
            lastUpdatedMs = latestUpdate,
            firstSeenMs = earliestSeen
        )
    }

    /**
     * Calculate exponential decay weight based on age.
     */
    private fun calculateDecayWeight(timestampMs: Long, currentTimeMs: Long): Float {
        val ageMs = currentTimeMs - timestampMs
        val ageDays = ageMs / (24 * 60 * 60 * 1000.0)
        return exp(-decayFactor * ageDays / DECAY_HALF_LIFE_DAYS).toFloat()
    }

    /**
     * Calculate the confidence score for a category prediction.
     * Takes into account observation count and distribution entropy.
     */
    fun calculateConfidence(distribution: MerchantCategoryDistribution): Float {
        if (!distribution.hasReliableHistory) {
            return 0.0f
        }

        val mostLikely = distribution.mostLikelyCategory ?: return 0.0f
        
        // Factor 1: Raw probability
        val probFactor = mostLikely.probability

        // Factor 2: Observation count factor (diminishing returns)
        val obsCount = distribution.totalObservations.toFloat()
        val obsFactor = 1 - exp(-obsCount / 10.0).toFloat()

        // Factor 3: Entropy factor (lower entropy = higher confidence)
        val maxEntropy = ln(distribution.probabilities.size.toFloat().coerceAtLeast(2f))
        val entropyFactor = if (maxEntropy > 0) {
            1 - (distribution.entropy / maxEntropy).coerceIn(0f, 1f)
        } else 1f

        return (probFactor * 0.5f + obsFactor * 0.25f + entropyFactor * 0.25f)
            .coerceIn(0f, 1f)
    }

    companion object {
        const val DEFAULT_SMOOTHING = 1.0f
        const val DEFAULT_DECAY = 0.0f // No decay by default
        const val DEFAULT_MIN_OBSERVATIONS = 3
        const val DECAY_HALF_LIFE_DAYS = 90.0
    }
}
