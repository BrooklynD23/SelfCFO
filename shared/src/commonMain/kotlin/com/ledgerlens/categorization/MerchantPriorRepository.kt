package com.ledgerlens.categorization

/**
 * Repository interface for persisting and retrieving merchant category priors.
 * Implementations should be thread-safe for concurrent access.
 */
interface MerchantPriorRepository {

    /**
     * Get the prior for a specific merchant.
     * @param merchantId Normalized merchant identifier
     * @return The prior, or null if no history exists
     */
    suspend fun getPrior(merchantId: String): MerchantPrior?

    /**
     * Get priors for multiple merchants in batch.
     * @param merchantIds List of normalized merchant identifiers
     * @return Map of merchantId to prior (missing merchants not included)
     */
    suspend fun getPriors(merchantIds: List<String>): Map<String, MerchantPrior>

    /**
     * Save or update a merchant prior.
     * @param prior The prior to save
     */
    suspend fun savePrior(prior: MerchantPrior)

    /**
     * Save multiple priors in batch.
     * @param priors List of priors to save
     */
    suspend fun savePriors(priors: List<MerchantPrior>)

    /**
     * Record a category assignment for a merchant.
     * Updates the prior incrementally without full recalculation.
     *
     * @param merchantId Normalized merchant identifier
     * @param categoryId Assigned category
     * @param transactionDateMs Transaction date in milliseconds since epoch
     */
    suspend fun recordCategoryAssignment(
        merchantId: String,
        categoryId: String,
        transactionDateMs: Long
    )

    /**
     * Record multiple category assignments in batch.
     * @param assignments List of (merchantId, categoryId, transactionDateMs) triples
     */
    suspend fun recordCategoryAssignments(
        assignments: List<CategoryAssignment>
    )

    /**
     * Delete the prior for a specific merchant.
     * @param merchantId Normalized merchant identifier
     */
    suspend fun deletePrior(merchantId: String)

    /**
     * Get all merchants that have been assigned to a specific category.
     * @param categoryId The category to search for
     * @return List of merchant IDs
     */
    suspend fun getMerchantsForCategory(categoryId: String): List<String>

    /**
     * Get the top N merchants by transaction count.
     * @param limit Maximum number of merchants to return
     * @return List of priors sorted by total transactions descending
     */
    suspend fun getTopMerchants(limit: Int): List<MerchantPrior>

    /**
     * Clear all merchant priors.
     * Use with caution - typically only for testing or full reset.
     */
    suspend fun clearAll()

    /**
     * Get total count of merchants with priors.
     */
    suspend fun count(): Int
}

/**
 * Data class for batch category assignment recording.
 */
data class CategoryAssignment(
    val merchantId: String,
    val categoryId: String,
    val transactionDateMs: Long
)
