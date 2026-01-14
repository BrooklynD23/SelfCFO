package com.ledgerlens.categorization

import kotlin.math.exp
import kotlin.math.ln

/**
 * Calculates category probabilities from merchant history using Bayesian inference.
 * 
 * Features:
 * - Time-based decay: Recent transactions are weighted more heavily
 * - Laplace smoothing: Prevents zero probabilities for unseen categories
 * - Configurable parameters for different use cases
 * 
 * The probability calculation uses:
 * P(category|merchant) = (decayed_count + pseudo_count) / (total_decayed + num_categories * pseudo_count)
 */
class PriorCalculator(
    private val params: PriorCalculationParams = PriorCalculationParams()
) {
    private val decayLambda = ln(2.0) / (params.decayHalfLifeDays * MILLIS_PER_DAY)

    /**
     * Calculate category probabilities for a merchant based on historical assignments.
     * 
     * @param prior The merchant's category assignment history
     * @param categoryAssignments Detailed assignment history with timestamps (optional, for decay)
     * @param currentTimeMs Current time for decay calculation
     * @param allCategoryIds All possible category IDs for smoothing (optional)
     * @return Distribution of category probabilities
     */
    fun calculateProbabilities(
        prior: MerchantPrior,
        categoryAssignments: List<TimestampedAssignment>? = null,
        currentTimeMs: Long = System.currentTimeMillis(),
        allCategoryIds: Set<String>? = null
    ): MerchantCategoryDistribution {
        if (prior.totalTransactions < params.minObservationsForPrior) {
            return MerchantCategoryDistribution.empty(prior.merchantId)
        }

        // If we have detailed timestamps, use decay weighting
        return if (categoryAssignments != null && categoryAssignments.isNotEmpty()) {
            calculateWithDecay(prior.merchantId, categoryAssignments, currentTimeMs, allCategoryIds)
        } else {
            calculateWithoutDecay(prior, allCategoryIds)
        }
    }

    /**
     * Calculate probabilities using time-based decay weighting.
     */
    private fun calculateWithDecay(
        merchantId: String,
        assignments: List<TimestampedAssignment>,
        currentTimeMs: Long,
        allCategoryIds: Set<String>?
    ): MerchantCategoryDistribution {
        // Calculate decayed weights per category
        val decayedCounts = mutableMapOf<String, Float>()
        var totalDecayedWeight = 0f

        for (assignment in assignments) {
            val ageMs = currentTimeMs - assignment.timestampMs
            val decayWeight = calculateDecayWeight(ageMs)
            
            decayedCounts[assignment.categoryId] = 
                (decayedCounts[assignment.categoryId] ?: 0f) + decayWeight
            totalDecayedWeight += decayWeight
        }

        // Get all categories to consider (observed + optional all categories for smoothing)
        val categoriesToConsider = if (allCategoryIds != null) {
            decayedCounts.keys + allCategoryIds
        } else {
            decayedCounts.keys
        }

        val numCategories = categoriesToConsider.size
        val smoothingDenominator = totalDecayedWeight + (numCategories * params.pseudoCount)

        // Calculate smoothed probabilities
        val probabilities = categoriesToConsider.map { categoryId ->
            val decayedCount = decayedCounts[categoryId] ?: 0f
            val smoothedProbability = (decayedCount + params.pseudoCount) / smoothingDenominator

            CategoryProbability(
                categoryId = categoryId,
                probability = smoothedProbability,
                observedCount = assignments.count { it.categoryId == categoryId },
                decayedWeight = decayedCount
            )
        }.sortedByDescending { it.probability }

        return MerchantCategoryDistribution(
            merchantId = merchantId,
            probabilities = probabilities,
            totalObservations = assignments.size,
            effectiveObservations = totalDecayedWeight
        )
    }

    /**
     * Calculate probabilities using simple frequency (no decay).
     */
    private fun calculateWithoutDecay(
        prior: MerchantPrior,
        allCategoryIds: Set<String>?
    ): MerchantCategoryDistribution {
        val categoriesToConsider = if (allCategoryIds != null) {
            prior.categoryCounts.keys + allCategoryIds
        } else {
            prior.categoryCounts.keys
        }

        val numCategories = categoriesToConsider.size
        val smoothingDenominator = prior.totalTransactions + (numCategories * params.pseudoCount)

        val probabilities = categoriesToConsider.map { categoryId ->
            val count = prior.countForCategory(categoryId)
            val smoothedProbability = (count + params.pseudoCount) / smoothingDenominator

            CategoryProbability(
                categoryId = categoryId,
                probability = smoothedProbability,
                observedCount = count,
                decayedWeight = count.toFloat()
            )
        }.sortedByDescending { it.probability }

        return MerchantCategoryDistribution(
            merchantId = prior.merchantId,
            probabilities = probabilities,
            totalObservations = prior.totalTransactions,
            effectiveObservations = prior.totalTransactions.toFloat()
        )
    }

    /**
     * Calculate the decay weight for a transaction given its age.
     * Uses exponential decay: weight = exp(-lambda * age)
     * 
     * @param ageMs Age of the transaction in milliseconds
     * @return Decay weight between 0 and 1
     */
    fun calculateDecayWeight(ageMs: Long): Float {
        if (ageMs <= 0) return 1f
        return exp(-decayLambda * ageMs).toFloat()
    }

    /**
     * Compute the posterior probability for a category given:
     * - Prior from merchant history
     * - Likelihood from classifier
     * 
     * Uses Bayes' theorem: P(C|M,E) ∝ P(E|C) * P(C|M)
     * where C = category, M = merchant, E = evidence (other features)
     * 
     * @param priorProbability P(category|merchant) from merchant history
     * @param likelihood P(evidence|category) from classifier
     * @param priors Map of all categories to their prior probabilities
     * @param likelihoods Map of all categories to their likelihoods
     * @return Posterior probability
     */
    fun computePosterior(
        categoryId: String,
        priorProbability: Float,
        likelihood: Float,
        priors: Map<String, Float>,
        likelihoods: Map<String, Float>
    ): Float {
        // Compute normalization constant (evidence)
        var evidence = 0f
        for ((cat, prior) in priors) {
            val catLikelihood = likelihoods[cat] ?: 0f
            evidence += prior * catLikelihood
        }

        if (evidence == 0f) return 0f

        // Posterior = (likelihood * prior) / evidence
        return (likelihood * priorProbability) / evidence
    }

    /**
     * Combine merchant prior with classifier confidence using weighted average.
     * Simpler than full Bayesian update, but effective for many use cases.
     * 
     * @param merchantPriorProbability Probability from merchant history
     * @param classifierConfidence Confidence from rule/ML classifier
     * @param merchantWeight Weight for merchant prior (0-1)
     * @return Combined score
     */
    fun combineScores(
        merchantPriorProbability: Float,
        classifierConfidence: Float,
        merchantWeight: Float = DEFAULT_MERCHANT_WEIGHT
    ): Float {
        require(merchantWeight in 0f..1f) { "Merchant weight must be between 0 and 1" }
        return (merchantWeight * merchantPriorProbability) + 
               ((1 - merchantWeight) * classifierConfidence)
    }

    companion object {
        private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000.0
        const val DEFAULT_MERCHANT_WEIGHT = 0.4f
    }
}

/**
 * A category assignment with timestamp for decay calculation.
 */
data class TimestampedAssignment(
    val categoryId: String,
    val timestampMs: Long
)
