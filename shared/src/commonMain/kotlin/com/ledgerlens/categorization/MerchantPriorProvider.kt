package com.ledgerlens.categorization

/**
 * Interface for classifiers to query merchant category priors.
 * Abstracts the repository and calculation logic from classification.
 */
interface MerchantPriorProvider {

    /**
     * Get the category probability distribution for a merchant.
     * 
     * @param merchantId Normalized merchant identifier
     * @return Category distribution, or empty distribution if no history
     */
    suspend fun getCategoryDistribution(merchantId: String): MerchantCategoryDistribution

    /**
     * Get category distributions for multiple merchants in batch.
     * 
     * @param merchantIds List of normalized merchant identifiers
     * @return Map of merchantId to category distribution
     */
    suspend fun getCategoryDistributions(
        merchantIds: List<String>
    ): Map<String, MerchantCategoryDistribution>

    /**
     * Check if a merchant has sufficient history for reliable priors.
     * 
     * @param merchantId Normalized merchant identifier
     * @return true if the merchant has reliable category history
     */
    suspend fun hasReliableHistory(merchantId: String): Boolean

    /**
     * Get the most likely category for a merchant based on history.
     * 
     * @param merchantId Normalized merchant identifier
     * @return The most likely category ID and probability, or null if no history
     */
    suspend fun getMostLikelyCategory(merchantId: String): Pair<String, Float>?

    /**
     * Notify the provider that a category was assigned to a merchant.
     * This allows the provider to update its internal state.
     * 
     * @param merchantId Normalized merchant identifier
     * @param categoryId Assigned category
     * @param transactionDateMs Transaction date in milliseconds
     */
    suspend fun recordAssignment(
        merchantId: String,
        categoryId: String,
        transactionDateMs: Long = System.currentTimeMillis()
    )

    /**
     * Trigger a batch recalculation of priors.
     * Useful after user corrections or bulk imports.
     * 
     * @param merchantIds Specific merchants to recalculate, or null for all
     */
    suspend fun recalculatePriors(merchantIds: List<String>? = null)
}

/**
 * Default implementation of MerchantPriorProvider.
 * Combines repository access with prior calculation.
 */
class DefaultMerchantPriorProvider(
    private val repository: MerchantPriorRepository,
    private val calculator: PriorCalculator = PriorCalculator(),
    private val allCategoryIds: Set<String>? = null
) : MerchantPriorProvider {

    override suspend fun getCategoryDistribution(merchantId: String): MerchantCategoryDistribution {
        val prior = repository.getPrior(merchantId)
            ?: return MerchantCategoryDistribution.empty(merchantId)

        return calculator.calculateProbabilities(
            prior = prior,
            allCategoryIds = allCategoryIds
        )
    }

    override suspend fun getCategoryDistributions(
        merchantIds: List<String>
    ): Map<String, MerchantCategoryDistribution> {
        val priors = repository.getPriors(merchantIds)

        return merchantIds.associateWith { merchantId ->
            val prior = priors[merchantId]
            if (prior != null) {
                calculator.calculateProbabilities(
                    prior = prior,
                    allCategoryIds = allCategoryIds
                )
            } else {
                MerchantCategoryDistribution.empty(merchantId)
            }
        }
    }

    override suspend fun hasReliableHistory(merchantId: String): Boolean {
        val prior = repository.getPrior(merchantId) ?: return false
        return prior.totalTransactions >= MerchantCategoryDistribution.MIN_RELIABLE_OBSERVATIONS.toInt()
    }

    override suspend fun getMostLikelyCategory(merchantId: String): Pair<String, Float>? {
        val distribution = getCategoryDistribution(merchantId)
        val mostLikely = distribution.mostLikelyCategory ?: return null
        return mostLikely.categoryId to mostLikely.probability
    }

    override suspend fun recordAssignment(
        merchantId: String,
        categoryId: String,
        transactionDateMs: Long
    ) {
        repository.recordCategoryAssignment(merchantId, categoryId, transactionDateMs)
    }

    override suspend fun recalculatePriors(merchantIds: List<String>?) {
        // For in-memory implementation, priors are always up-to-date
        // SQLDelight implementation would re-aggregate from transaction history
    }
}

/**
 * A classifier that uses merchant priors as primary classification source.
 * Integrates with the TransactionClassifier interface.
 */
class MerchantPriorClassifier(
    private val priorProvider: MerchantPriorProvider,
    private val minConfidenceThreshold: Float = DEFAULT_MIN_CONFIDENCE
) : TransactionClassifier {

    override val name: String = "merchant-prior"
    override val priority: Int = 80

    private var cachedDistribution: MerchantCategoryDistribution? = null

    override fun classify(features: TransactionFeatures): ClassificationResult {
        // Note: This is a synchronous interface, but we need async data
        // In practice, the distribution should be pre-fetched before classification
        val distribution = cachedDistribution
            ?: return ClassificationResult.unknown()

        if (!distribution.isReliable) {
            return ClassificationResult.unknown()
        }

        val mostLikely = distribution.mostLikelyCategory
            ?: return ClassificationResult.unknown()

        if (mostLikely.probability < minConfidenceThreshold) {
            return ClassificationResult.unknown()
        }

        val alternatives = distribution.probabilities
            .filter { it.categoryId != mostLikely.categoryId }
            .take(3)
            .map { CategoryScore(it.categoryId, it.probability) }

        return ClassificationResult(
            categoryId = mostLikely.categoryId,
            confidence = mostLikely.probability,
            alternatives = alternatives,
            explanation = ClassificationExplanation(
                classifierUsed = name,
                reason = "Based on ${distribution.totalObservations} previous transactions",
                merchantPrior = mostLikely.probability
            )
        )
    }

    override fun canClassify(features: TransactionFeatures): Boolean {
        return features.merchantNormalized.isNotBlank()
    }

    /**
     * Pre-fetch the distribution for a merchant before classification.
     * Call this before classify() in async context.
     */
    suspend fun prefetchDistribution(merchantId: String) {
        cachedDistribution = priorProvider.getCategoryDistribution(merchantId)
    }

    /**
     * Clear the cached distribution.
     */
    fun clearCache() {
        cachedDistribution = null
    }

    companion object {
        const val DEFAULT_MIN_CONFIDENCE = 0.3f
    }
}
